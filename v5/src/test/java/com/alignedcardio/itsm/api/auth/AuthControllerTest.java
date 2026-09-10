package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private LocationRepository locationRepository;

    private Jwt testJwt;

    @BeforeEach
    void setUp() {
        testJwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user-id")
                .claim("email", "test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void testAuthMeEndpointWithEagerRoleLoading() throws Exception {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setObjectId("test-user-id");
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setActive(true);
        user.setMfaEnabled(false);

        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("END_USER");
        role.setDescription("End user role");

        UserRole userRole = new UserRole();
        userRole.setId(UUID.randomUUID());
        userRole.setUser(user);
        userRole.setRole(role);

        Set<UserRole> userRoles = new HashSet<>();
        userRoles.add(userRole);
        user.setUserRoles(userRoles);

        when(userService.syncFromJwt(any(Jwt.class))).thenReturn(user);
        when(userService.toCurrentUser(any(AppUser.class))).thenReturn(new CurrentUser(
                user.getId(), user.getObjectId(), user.getEmail(), user.getDisplayName(),
                null, null, java.util.List.of("END_USER"), true, false, null, false,
                java.util.List.of(), java.util.List.of()));

        mockMvc.perform(get("/api/auth/me")
                .with(jwt().jwt(testJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.roles[0]").value("END_USER"));
    }

    @Test
    void testAuthMeEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
