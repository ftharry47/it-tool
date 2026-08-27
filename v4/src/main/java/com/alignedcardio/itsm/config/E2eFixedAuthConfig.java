package com.alignedcardio.itsm.config;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.RoleRepository;
import com.alignedcardio.itsm.repository.UserRoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * FIXED-TOKEN E2E AUTH — this configuration is intentionally and structurally
 * gated behind the "e2e-fixed-auth" Spring profile. The beans in this class
 * are never constructed in any other profile, so the fixed-token decoder
 * cannot be enabled at runtime by an accidental Application Setting.
 *
 * To use this profile:
 *   java -jar target/app.jar --spring.profiles.active=e2e-fixed-auth
 * or set SPRING_PROFILES_ACTIVE=e2e-fixed-auth.
 */
@Configuration
@Profile("e2e-fixed-auth")
public class E2eFixedAuthConfig {

    private static final Set<String> VALID_TOKENS = Set.of(
            "test-end-user",
            "test-agent"
    );

    @Bean
    public JwtDecoder e2eFixedJwtDecoder() {
        return token -> {
            if (!VALID_TOKENS.contains(token)) {
                throw new BadJwtException("Unknown E2E token");
            }

            String objectId = token;
            String username = token + "@alignedcardio.example";
            String displayName = token.equals("test-agent")
                    ? "Test Agent"
                    : "Test End User";

            Map<String, Object> headers = Map.of("alg", "none");
            Map<String, Object> claims = Map.of(
                    "sub", objectId,
                    "oid", objectId,
                    "preferred_username", username,
                    "email", username,
                    "name", displayName
            );

            return new Jwt(
                    token,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    headers,
                    claims
            );
        };
    }

    @Bean
    public CommandLineRunner e2eUserSeeder(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository
    ) {
        return args -> {
            seedUser(appUserRepository, roleRepository, userRoleRepository,
                    "test-end-user", "END_USER");
            seedUser(appUserRepository, roleRepository, userRoleRepository,
                    "test-agent", "AGENT");
        };
    }

    private void seedUser(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            String objectId,
            String roleName
    ) {
        var orgId = BaseEntity.DEFAULT_ORG_ID;

        if (appUserRepository.findByOrgIdAndObjectId(orgId, objectId).isPresent()) {
            return;
        }

        Role role = roleRepository.findByOrgIdAndName(orgId, roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " not found; migrations may not have run"));

        AppUser user = new AppUser();
        user.setOrgId(orgId);
        user.setObjectId(objectId);
        user.setEmail(objectId + "@alignedcardio.example");
        user.setDisplayName(roleName.equals("AGENT") ? "Test Agent" : "Test End User");
        user.setJobTitle("E2E Test");
        user.setDepartment("QA");
        user.setActive(true);
        user.setMfaEnabled(false);
        user.setUserRoles(new HashSet<>());

        user = appUserRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setOrgId(orgId);
        userRole.setUser(user);
        userRole.setRole(role);
        userRole = userRoleRepository.save(userRole);

        user.getUserRoles().add(userRole);
    }
}
