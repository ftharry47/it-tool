package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.auth.CurrentUser;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.RoleRepository;
import com.alignedcardio.itsm.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alignedcardio.itsm.api.auth.UpdateUserRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private com.alignedcardio.itsm.repository.TeamMemberRepository teamMemberRepository;

    private ObjectMapper objectMapper;
    private UserService userService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userService = new UserService(appUserRepository, roleRepository, userRoleRepository,
                auditLogRepository, locationRepository, teamMemberRepository, objectMapper);
    }

    @Test
    void updateRoleWritesAuditLogWithBeforeAndAfterState() {
        UUID orgId = BaseEntity.DEFAULT_ORG_ID;
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AppUser user = new AppUser();
        user.setId(userId);
        user.setOrgId(orgId);

        Role oldRole = new Role();
        oldRole.setName("END_USER");
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(oldRole);
        user.setUserRoles(new HashSet<>(List.of(userRole)));

        Role newRole = new Role();
        newRole.setName("ADMIN");

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByOrgIdAndName(orgId, "ADMIN")).thenReturn(Optional.of(newRole));

        CurrentUser result = userService.updateRole(userId, "ADMIN", actorId, "127.0.0.1");

        assertNotNull(result);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertEquals(orgId, log.getOrgId());
        assertEquals(actorId, log.getActorUserId());
        assertEquals("USER", log.getEntityType());
        assertEquals(userId, log.getEntityId());
        assertEquals("UPDATE_ROLE", log.getAction());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertTrue(log.getBeforeState().contains("END_USER"));
        assertTrue(log.getAfterState().contains("ADMIN"));
    }

    @Test
    void syncFromJwtRejectsDeactivatedUser() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("oid")).thenReturn("oid-1");
        when(jwt.getClaimAsString("preferred_username")).thenReturn("a@b.com");

        AppUser user = new AppUser();
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        user.setObjectId("oid-1");
        user.setActive(false);

        when(appUserRepository.findByOrgIdAndObjectId(BaseEntity.DEFAULT_ORG_ID, "oid-1"))
                .thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> userService.syncFromJwt(jwt));
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void updateUserAppliesFlags() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        AppUser user = new AppUser();
        user.setId(userId);
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        user.setActive(true);
        user.setMfaEnabled(false);

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        CurrentUser result = userService.updateUser(userId,
                new UpdateUserRequest(false, true, managerId), actorId);

        assertFalse(result.isActive());
        assertTrue(result.mfaEnabled());
        assertEquals(managerId, result.managerId());
        assertEquals(actorId, user.getUpdatedBy());
    }
}
