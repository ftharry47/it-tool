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
 * LOCAL PREVIEW AUTH — this configuration is intentionally and structurally
 * gated behind the "local" Spring profile. It is the same pattern as
 * {@link E2eFixedAuthConfig}: a fixed-token decoder and seeded test users.
 * These beans are never constructed unless the "local" profile is explicitly
 * active, so the fixed-token decoder cannot be enabled in production by
 * accident.
 *
 * To use this profile:
 *   java -jar target/app.jar --spring.profiles.active=local
 * or set SPRING_PROFILES_ACTIVE=local.
 */
@Configuration
@Profile("local")
public class LocalAuthConfig {

    private static final Set<String> VALID_TOKENS = Set.of(
            "local-admin",
            "local-user"
    );

    @Bean
    public JwtDecoder localJwtDecoder() {
        return token -> {
            if (!VALID_TOKENS.contains(token)) {
                throw new BadJwtException("Unknown local preview token");
            }

            String objectId = token;
            String username = token + "@alignedcardio.example";
            String displayName = token.equals("local-admin")
                    ? "Local Admin"
                    : "Local User";

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
    public CommandLineRunner localUserSeeder(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository
    ) {
        return args -> {
            seedUser(appUserRepository, roleRepository, userRoleRepository,
                    "local-admin", "SUPER_ADMIN");
            seedUser(appUserRepository, roleRepository, userRoleRepository,
                    "local-user", "END_USER");
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
        user.setDisplayName(roleName.equals("SUPER_ADMIN") ? "Local Admin" : "Local User");
        user.setJobTitle("Local Preview");
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
