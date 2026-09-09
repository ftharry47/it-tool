package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.auth.CurrentUser;
import com.alignedcardio.itsm.api.auth.UpdateUserRequest;
import com.alignedcardio.itsm.api.user.UserCreateRequest;
import com.alignedcardio.itsm.api.user.UserResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.RoleRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private static final String DEFAULT_ROLE = "END_USER";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogRepository auditLogRepository;
    private final LocationRepository locationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ObjectMapper objectMapper;

    public UserService(AppUserRepository appUserRepository,
                       RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository,
                       AuditLogRepository auditLogRepository,
                       LocationRepository locationRepository,
                       TeamMemberRepository teamMemberRepository,
                       ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogRepository = auditLogRepository;
        this.locationRepository = locationRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AppUser syncFromJwt(Jwt jwt) {
        String objectId = Optional.ofNullable(jwt.getClaimAsString("oid"))
                .orElse(jwt.getSubject());

        String email = Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                .orElse(jwt.getClaimAsString("email"));

        String displayName = Optional.ofNullable(jwt.getClaimAsString("name"))
                .orElse(jwt.getClaimAsString("displayName"));

        String jobTitle = jwt.getClaimAsString("jobTitle");
        String department = jwt.getClaimAsString("department");

        AppUser user = appUserRepository.findByOrgIdAndObjectId(BaseEntity.DEFAULT_ORG_ID, objectId)
                .map(existing -> {
                    if (!existing.isActive()) {
                        throw new DisabledException("User account is deactivated");
                    }
                    existing.setEmail(email);
                    existing.setDisplayName(displayName);
                    existing.setJobTitle(jobTitle);
                    existing.setDepartment(department);
                    return existing;
                })
                .orElseGet(() -> createUser(objectId, email, displayName, jobTitle, department));

        return appUserRepository.save(user);
    }

    private AppUser createUser(String objectId, String email, String displayName, String jobTitle, String department) {
        AppUser user = new AppUser();
        user.setObjectId(objectId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setJobTitle(jobTitle);
        user.setDepartment(department);
        user.setStatus(AppUser.Status.ACTIVE);
        user.setActive(true);
        user.setMfaEnabled(false);

        Role role = roleRepository.findByOrgIdAndName(BaseEntity.DEFAULT_ORG_ID, DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not seeded"));

        user = appUserRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setOrgId(user.getOrgId());
        userRoleRepository.save(userRole);

        return user;
    }

    @Transactional(readOnly = true)
    public List<CurrentUser> findAllByOrgIdDefault() {
        return appUserRepository.findByOrgId(BaseEntity.DEFAULT_ORG_ID).stream()
                .map(this::toCurrentUser)
                .toList();
    }

    @Transactional
    public CurrentUser updateUser(UUID userId, UpdateUserRequest request, UUID actorId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }
        if (request.mfaEnabled() != null) {
            user.setMfaEnabled(request.mfaEnabled());
        }
        if (request.managerId() != null) {
            user.setManagerId(request.managerId());
        }
        user.setUpdatedBy(actorId);

        return toCurrentUser(appUserRepository.save(user));
    }

    @Transactional
    public CurrentUser updateRole(UUID userId, String roleName, UUID actorId, String ipAddress) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Role role = roleRepository.findByOrgIdAndName(user.getOrgId(), roleName)
                .orElseThrow(() -> new NotFoundException("Role not found"));

        List<String> beforeRoles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        if (beforeRoles.size() == 1 && beforeRoles.get(0).equals(roleName)) {
            return toCurrentUser(user);
        }

        user.getUserRoles().clear();

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setOrgId(user.getOrgId());
        userRole.setCreatedBy(actorId);
        userRole.setUpdatedBy(actorId);
        user.getUserRoles().add(userRole);

        userRoleRepository.save(userRole);

        user.setUpdatedBy(actorId);
        appUserRepository.save(user);

        writeAuditLog(user, actorId, "UPDATE_ROLE", beforeRoles, List.of(roleName), ipAddress);

        return toCurrentUser(user);
    }

    private void writeAuditLog(AppUser user, UUID actorId, String action,
                               List<String> before, List<String> after, String ipAddress) {
        try {
            ArrayNode beforeNode = objectMapper.valueToTree(before);
            ArrayNode afterNode = objectMapper.valueToTree(after);

            AuditLog log = new AuditLog();
            log.setOrgId(user.getOrgId());
            log.setActorUserId(actorId);
            log.setAction(action);
            log.setEntityType("USER");
            log.setEntityId(user.getId());
            log.setBeforeState(beforeNode.toString());
            log.setAfterState(afterNode.toString());
            log.setIpAddress(ipAddress);

            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for user role update", e);
        }
    }

    @Transactional
    public UserResponse createLocalUser(UUID orgId, UserCreateRequest request) {
        if (appUserRepository.findByOrgIdAndEmailIgnoreCase(orgId, request.email()).isPresent()) {
            throw new IllegalStateException("A user with email " + request.email() + " already exists");
        }

        Role role = roleRepository.findByOrgIdAndName(orgId, request.roleName())
                .orElseThrow(() -> new NotFoundException("Role not found"));

        AppUser user = new AppUser();
        user.setOrgId(orgId);
        user.setObjectId(UUID.randomUUID().toString());
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setJobTitle(request.jobTitle());
        user.setDepartment(request.department());
        user.setStatus(AppUser.Status.ACTIVE);
        user.setActive(true);
        user.setMfaEnabled(false);

        user = appUserRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setOrgId(orgId);
        userRoleRepository.save(userRole);
        user.getUserRoles().add(userRole);

        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByOrg(UUID orgId) {
        return appUserRepository.findByOrgId(orgId).stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(UUID orgId, String query, int limit) {
        return appUserRepository.searchByText(orgId, query, PageRequest.of(0, limit)).stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getByOrgAndId(UUID orgId, UUID userId) {
        AppUser user = appUserRepository.findByOrgIdAndId(orgId, userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getJobTitle(),
                user.getDepartment(),
                user.isActive(),
                user.getUserRoles().stream().map(ur -> ur.getRole().getName()).toList()
        );
    }

    public CurrentUser toCurrentUser(AppUser user) {
        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        return new CurrentUser(
                user.getId(),
                user.getObjectId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getJobTitle(),
                user.getDepartment(),
                roles,
                user.isActive(),
                user.isMfaEnabled(),
                user.getManagerId(),
                locationRepository.existsByApprovalManager_IdAndDeletedAtIsNull(user.getId()),
                teamMemberRepository.findByUserId(user.getId()).stream()
                        .map(tm -> tm.getTeam() != null ? tm.getTeam().getId() : tm.getTeamId())
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                teamMemberRepository.findByUserId(user.getId()).stream()
                        .map(tm -> tm.getTeam() != null ? tm.getTeam().getName() : null)
                        .filter(java.util.Objects::nonNull)
                        .toList()
        );
    }
}
