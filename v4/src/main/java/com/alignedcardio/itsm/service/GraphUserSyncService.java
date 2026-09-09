package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.user.AdSyncResult;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.RoleRepository;
import com.alignedcardio.itsm.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * On-demand sync of Entra ID (Azure AD) users into app_user via Microsoft Graph.
 * Uses the OAuth2 client-credentials flow (app-only) — requires User.Read.All
 * application permission with admin consent.
 *
 * Matching: app_user.object_id stores the Entra object id (set by JIT
 * provisioning). Local users created before their first login carry a random
 * UUID placeholder, so we fall back to an email match and then repair object_id.
 *
 * Rules: existing users get display name/email/job title/department refreshed
 * only — role and is_active are never overwritten. New users are created with
 * the default END_USER role and is_active = Graph accountEnabled.
 *
 * The whole sync runs in one transaction: any Graph failure mid-sync rolls back
 * all writes, so a partial sync can never silently succeed.
 */
@Service
public class GraphUserSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GraphUserSyncService.class);
    private static final String DEFAULT_ROLE = "END_USER";
    private static final String USERS_URL =
            "https://graph.microsoft.com/v1.0/users?$select=id,displayName,mail,userPrincipalName,accountEnabled,jobTitle,department&$top=999";

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Autowired
    public GraphUserSyncService(
            @Value("${azure.graph.tenant-id:}") String tenantId,
            @Value("${azure.graph.client-id:}") String clientId,
            @Value("${azure.graph.client-secret:}") String clientSecret,
            ObjectMapper objectMapper,
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this(tenantId, clientId, clientSecret, RestClient.builder().build(),
                objectMapper, appUserRepository, roleRepository, userRoleRepository);
    }

    // Package-private seam for tests — injects a stubbed RestClient.
    GraphUserSyncService(String tenantId, String clientId, String clientSecret,
                         RestClient restClient, ObjectMapper objectMapper,
                         AppUserRepository appUserRepository,
                         RoleRepository roleRepository,
                         UserRoleRepository userRoleRepository) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional
    public AdSyncResult sync(UUID orgId) {
        if (isBlank(tenantId) || isBlank(clientId) || isBlank(clientSecret)) {
            throw new IllegalStateException(
                    "AD sync is not configured. Set AZURE_GRAPH_TENANT_ID, AZURE_GRAPH_CLIENT_ID and AZURE_GRAPH_CLIENT_SECRET.");
        }

        String token = acquireToken();

        int added = 0, updated = 0, skipped = 0;
        String url = USERS_URL;
        while (url != null) {
            JsonNode page = fetchJson(url, token);
            for (JsonNode adUser : page.path("value")) {
                Upsert outcome = upsert(orgId, adUser);
                switch (outcome) {
                    case ADDED -> added++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            }
            JsonNode next = page.get("@odata.nextLink");
            url = next != null && next.isTextual() ? next.asText() : null;
        }

        logger.info("AD sync complete for org {}: {} added, {} updated, {} skipped", orgId, added, updated, skipped);
        return new AdSyncResult(added, updated, skipped, 0);
    }

    private enum Upsert { ADDED, UPDATED, SKIPPED }

    private Upsert upsert(UUID orgId, JsonNode adUser) {
        String objectId = text(adUser, "id");
        String email = Optional.ofNullable(text(adUser, "mail"))
                .orElse(text(adUser, "userPrincipalName"));
        if (isBlank(objectId) || isBlank(email)) {
            return Upsert.SKIPPED;
        }

        String displayName = text(adUser, "displayName");
        String jobTitle = text(adUser, "jobTitle");
        String department = text(adUser, "department");
        boolean accountEnabled = adUser.path("accountEnabled").asBoolean(true);

        Optional<AppUser> existing = appUserRepository.findByOrgIdAndObjectId(orgId, objectId)
                .or(() -> appUserRepository.findByOrgIdAndEmailIgnoreCase(orgId, email));

        if (existing.isPresent()) {
            AppUser user = existing.get();
            // Repair placeholder object_id on local users matched by email.
            if (!objectId.equals(user.getObjectId())) {
                user.setObjectId(objectId);
            }
            // Refresh profile fields only — never touch role or is_active.
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setJobTitle(jobTitle);
            user.setDepartment(department);
            appUserRepository.save(user);
            return Upsert.UPDATED;
        }

        AppUser user = new AppUser();
        user.setOrgId(orgId);
        user.setObjectId(objectId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setJobTitle(jobTitle);
        user.setDepartment(department);
        user.setStatus(AppUser.Status.ACTIVE);
        user.setActive(accountEnabled);
        user.setMfaEnabled(false);
        user = appUserRepository.save(user);

        Role role = roleRepository.findByOrgIdAndName(orgId, DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not seeded"));
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setOrgId(orgId);
        userRoleRepository.save(userRole);

        return Upsert.ADDED;
    }

    // ---- Graph HTTP calls (seams for tests) ----

    String acquireToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", "https://graph.microsoft.com/.default");

        try {
            String response = restClient.post()
                    .uri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            String token = objectMapper.readTree(response).path("access_token").asText();
            if (isBlank(token)) {
                throw new IllegalStateException("Microsoft Graph returned an empty access token");
            }
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not authenticate to Microsoft Graph. Verify the client secret and app registration.", e);
        }
    }

    JsonNode fetchJson(String url, String token) {
        try {
            String response = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Microsoft Graph request failed mid-sync. Check that User.Read.All (Application) is granted with admin consent. No changes were saved.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
