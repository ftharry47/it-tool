package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.user.AdSyncResult;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.RoleRepository;
import com.alignedcardio.itsm.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphUserSyncServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID orgId = BaseEntity.DEFAULT_ORG_ID;

    /** Test subclass that stubs the two HTTP seams. */
    private static class StubSyncService extends GraphUserSyncService {
        private final Map<String, JsonNode> pages;
        private final RuntimeException tokenFailure;
        private final RuntimeException pageFailure;

        StubSyncService(Map<String, JsonNode> pages, RuntimeException tokenFailure, RuntimeException pageFailure,
                        ObjectMapper om, AppUserRepository u, RoleRepository r, UserRoleRepository ur) {
            super("tenant", "client-id", "secret", (RestClient) null, om, u, r, ur);
            this.pages = pages;
            this.tokenFailure = tokenFailure;
            this.pageFailure = pageFailure;
        }

        @Override
        String acquireToken() {
            if (tokenFailure != null) throw tokenFailure;
            return "fake-token";
        }

        @Override
        JsonNode fetchJson(String url, String token) {
            if (pageFailure != null) throw pageFailure;
            JsonNode page = pages.get(url);
            if (page == null) throw new IllegalStateException("Unexpected URL: " + url);
            return page;
        }
    }

    private static final String FIRST_PAGE_URL =
            "https://graph.microsoft.com/v1.0/users?$select=id,displayName,mail,userPrincipalName,accountEnabled,jobTitle,department&$top=999";

    private JsonNode page(String nextLink, String... users) throws Exception {
        StringBuilder sb = new StringBuilder("{\"value\":[");
        sb.append(String.join(",", users)).append("]");
        if (nextLink != null) sb.append(",\"@odata.nextLink\":\"").append(nextLink).append("\"");
        sb.append("}");
        return objectMapper.readTree(sb.toString());
    }

    private static String adUser(String id, String name, String mail, boolean enabled) {
        return "{\"id\":\"" + id + "\",\"displayName\":\"" + name + "\",\"mail\":\"" + mail
                + "\",\"accountEnabled\":" + enabled + ",\"jobTitle\":\"Tech\",\"department\":\"IT\"}";
    }

    private GraphUserSyncService service(Map<String, JsonNode> pages) {
        return new StubSyncService(pages, null, null, objectMapper,
                appUserRepository, roleRepository, userRoleRepository);
    }

    @BeforeEach
    void defaults() {
        lenient().when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void throwsWhenNotConfigured() {
        GraphUserSyncService unconfigured = new GraphUserSyncService(
                "", "client-id", "", (RestClient) null, objectMapper,
                appUserRepository, roleRepository, userRoleRepository);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> unconfigured.sync(orgId));
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    void createsNewUsersWithEndUserRole() throws Exception {
        Map<String, JsonNode> pages = Map.of(FIRST_PAGE_URL,
                page(null, adUser("ad-1", "Alice", "alice@corp.com", true)));
        Role endUser = new Role();
        endUser.setName("END_USER");
        when(roleRepository.findByOrgIdAndName(orgId, "END_USER")).thenReturn(Optional.of(endRole(endUser)));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(1, result.added());
        verify(appUserRepository).save(argThat(u ->
                "ad-1".equals(u.getObjectId()) && "alice@corp.com".equals(u.getEmail()) && u.isActive()));
        verify(userRoleRepository).save(argThat(ur -> "END_USER".equals(ur.getRole().getName())));
    }

    @Test
    void newDisabledAdUserIsCreatedInactive() throws Exception {
        Map<String, JsonNode> pages = Map.of(FIRST_PAGE_URL,
                page(null, adUser("ad-2", "Bob", "bob@corp.com", false)));
        when(roleRepository.findByOrgIdAndName(orgId, "END_USER")).thenReturn(Optional.of(endRole(new Role())));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(1, result.added());
        verify(appUserRepository).save(argThat(u -> !u.isActive()));
    }

    @Test
    void existingUserUpdatesProfileButKeepsRoleAndActive() throws Exception {
        AppUser existing = new AppUser();
        existing.setOrgId(orgId);
        existing.setObjectId("ad-3");
        existing.setEmail("old@corp.com");
        existing.setDisplayName("Old Name");
        existing.setActive(false); // admin deactivated — must NOT be re-enabled by sync
        Role admin = new Role();
        admin.setName("ADMIN");
        UserRole adminRole = new UserRole();
        adminRole.setUser(existing);
        adminRole.setRole(admin);
        existing.setUserRoles(new java.util.HashSet<>(java.util.List.of(adminRole)));

        when(appUserRepository.findByOrgIdAndObjectId(orgId, "ad-3")).thenReturn(Optional.of(existing));

        Map<String, JsonNode> pages = Map.of(FIRST_PAGE_URL,
                page(null, adUser("ad-3", "New Name", "new@corp.com", true)));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(1, result.updated());
        assertEquals(0, result.added());
        assertEquals("New Name", existing.getDisplayName());
        assertEquals("new@corp.com", existing.getEmail());
        assertFalse(existing.isActive()); // is_active untouched
        assertEquals("ADMIN", existing.getUserRoles().iterator().next().getRole().getName()); // role untouched
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void localUserMatchedByEmailGetsRealObjectId() throws Exception {
        AppUser local = new AppUser();
        local.setOrgId(orgId);
        local.setObjectId(UUID.randomUUID().toString()); // placeholder from createLocalUser
        local.setEmail("carol@corp.com");

        when(appUserRepository.findByOrgIdAndObjectId(orgId, "ad-4")).thenReturn(Optional.empty());
        when(appUserRepository.findByOrgIdAndEmailIgnoreCase(orgId, "carol@corp.com")).thenReturn(Optional.of(local));

        Map<String, JsonNode> pages = Map.of(FIRST_PAGE_URL,
                page(null, adUser("ad-4", "Carol", "carol@corp.com", true)));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(1, result.updated());
        assertEquals("ad-4", local.getObjectId());
    }

    @Test
    void followsNextLinkPagination() throws Exception {
        String page2 = "https://graph.microsoft.com/v1.0/users?$skiptoken=abc";
        Map<String, JsonNode> pages = new HashMap<>();
        pages.put(FIRST_PAGE_URL, page(page2, adUser("ad-5", "Dave", "dave@corp.com", true)));
        pages.put(page2, page(null, adUser("ad-6", "Erin", "erin@corp.com", true)));
        when(roleRepository.findByOrgIdAndName(orgId, "END_USER")).thenReturn(Optional.of(endRole(new Role())));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(2, result.added());
        verify(appUserRepository, times(2)).save(any(AppUser.class));
    }

    @Test
    void skipsAdUsersWithoutIdOrEmail() throws Exception {
        Map<String, JsonNode> pages = Map.of(FIRST_PAGE_URL,
                page(null,
                        "{\"displayName\":\"No Id\",\"mail\":\"x@corp.com\"}",
                        "{\"id\":\"ad-7\",\"displayName\":\"No Mail\"}"));

        AdSyncResult result = service(pages).sync(orgId);

        assertEquals(2, result.skipped());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void tokenFailurePropagatesAsIllegalState() {
        StubSyncService svc = new StubSyncService(Map.of(),
                new IllegalStateException("Could not authenticate to Microsoft Graph. Verify the client secret and app registration."),
                null, objectMapper, appUserRepository, roleRepository, userRoleRepository);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.sync(orgId));
        assertTrue(ex.getMessage().contains("authenticate"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void midSyncGraphFailurePropagatesAndWritesNothing() throws Exception {
        StubSyncService svc = new StubSyncService(Map.of(), null,
                new IllegalStateException("Microsoft Graph request failed mid-sync. Check that User.Read.All (Application) is granted with admin consent. No changes were saved."),
                objectMapper, appUserRepository, roleRepository, userRoleRepository);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.sync(orgId));
        assertTrue(ex.getMessage().contains("mid-sync"));
        verify(appUserRepository, never()).save(any());
    }

    private static Role endRole(Role r) {
        r.setName("END_USER");
        return r;
    }
}
