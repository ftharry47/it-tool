package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.user.UserCreateRequest;
import com.alignedcardio.itsm.api.user.UserResponse;
import com.alignedcardio.itsm.entity.AppUser;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceReproTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private LocationRepository locationRepository;
@Mock private TeamMemberRepository teamMemberRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(appUserRepository, roleRepository, userRoleRepository,
                auditLogRepository, locationRepository, teamMemberRepository, new ObjectMapper());
    }

    @Test
    void createLocalUserReturnsRoleInResponse() {
        UUID orgId = BaseEntity.DEFAULT_ORG_ID;
        Role role = new Role();
        role.setName("AGENT");

        when(roleRepository.findByOrgIdAndName(orgId, "AGENT")).thenReturn(Optional.of(role));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.createLocalUser(orgId,
                new UserCreateRequest("new@corp.com", "New User", "Dev", "IT", "AGENT"));

        // BUG: roles list is empty because userRole is saved via repo but never added to user.getUserRoles()
        assertEquals(List.of("AGENT"), result.roles(),
                "createLocalUser should return the assigned role in the response");
    }

    @Test
    void updateRoleClearsOldRoleAndAddsNew() {
        UUID orgId = BaseEntity.DEFAULT_ORG_ID;
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AppUser user = new AppUser();
        user.setId(userId);
        user.setOrgId(orgId);

        Role oldRole = new Role();
        oldRole.setName("END_USER");
        UserRole oldUserRole = new UserRole();
        oldUserRole.setUser(user);
        oldUserRole.setRole(oldRole);
        user.setUserRoles(new HashSet<>(List.of(oldUserRole)));

        Role newRole = new Role();
        newRole.setName("ADMIN");

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByOrgIdAndName(orgId, "ADMIN")).thenReturn(Optional.of(newRole));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = userService.updateRole(userId, "ADMIN", actorId, "127.0.0.1");

        assertEquals(List.of("ADMIN"), result.roles());
        assertEquals(1, user.getUserRoles().size());
        assertEquals("ADMIN", user.getUserRoles().iterator().next().getRole().getName());
    }
}
