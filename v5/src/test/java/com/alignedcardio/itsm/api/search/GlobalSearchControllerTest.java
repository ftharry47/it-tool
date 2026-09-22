package com.alignedcardio.itsm.api.search;

import com.alignedcardio.itsm.api.incident.IncidentSummary;
import com.alignedcardio.itsm.api.kb.KbSearchResult;
import com.alignedcardio.itsm.config.GlobalExceptionHandler;
import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.service.ChangeService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.KnowledgeBaseService;
import com.alignedcardio.itsm.service.ProblemService;
import com.alignedcardio.itsm.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalSearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class GlobalSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ChangeService changeService;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserSearchOnlyOwnIncidentsAndKb() throws Exception {
        AppUser user = userWithRole("END_USER");

        when(userService.syncFromJwt(any())).thenReturn(user);
        when(incidentService.search(eq(user.getOrgId()), eq("laptop"), eq(user.getId()), eq(10)))
                .thenReturn(List.of(sampleIncident()));
        when(knowledgeBaseService.search(eq(user.getOrgId()), eq("laptop")))
                .thenReturn(List.of(sampleKb()));

        mvc.perform(get("/api/v1/search").param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents").isArray())
                .andExpect(jsonPath("$.incidents.length()").value(1))
                .andExpect(jsonPath("$.kb").isArray())
                .andExpect(jsonPath("$.kb.length()").value(1))
                .andExpect(jsonPath("$.problems").isEmpty())
                .andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.users").isEmpty());

        verify(incidentService).search(user.getOrgId(), "laptop", user.getId(), 10);
        verify(incidentService, never()).search(eq(user.getOrgId()), anyString(), isNull(), anyInt());
        verify(problemService, never()).search(any(), anyString(), anyInt());
        verify(changeService, never()).search(any(), anyString(), anyInt());
        verify(userService, never()).search(any(), anyString(), anyInt());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentSearchSeesIncidentsProblemsChangesButNotUsers() throws Exception {
        AppUser user = userWithRole("AGENT");

        when(userService.syncFromJwt(any())).thenReturn(user);
        when(incidentService.search(eq(user.getOrgId()), eq("laptop"), isNull(), eq(10)))
                .thenReturn(List.of(sampleIncident()));
        when(problemService.search(eq(user.getOrgId()), eq("laptop"), eq(10)))
                .thenReturn(List.of());
        when(changeService.search(eq(user.getOrgId()), eq("laptop"), eq(10)))
                .thenReturn(List.of());
        when(knowledgeBaseService.search(eq(user.getOrgId()), eq("laptop")))
                .thenReturn(List.of(sampleKb()));

        mvc.perform(get("/api/v1/search").param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty());

        verify(incidentService).search(user.getOrgId(), "laptop", null, 10);
        verify(problemService).search(user.getOrgId(), "laptop", 10);
        verify(changeService).search(user.getOrgId(), "laptop", 10);
        verify(userService, never()).search(any(), anyString(), anyInt());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void adminSearchIncludesUsers() throws Exception {
        AppUser user = userWithRole("SUPER_ADMIN");

        when(userService.syncFromJwt(any())).thenReturn(user);
        when(incidentService.search(eq(user.getOrgId()), eq("laptop"), isNull(), eq(10)))
                .thenReturn(List.of());
        when(problemService.search(eq(user.getOrgId()), eq("laptop"), eq(10)))
                .thenReturn(List.of());
        when(changeService.search(eq(user.getOrgId()), eq("laptop"), eq(10)))
                .thenReturn(List.of());
        when(knowledgeBaseService.search(eq(user.getOrgId()), eq("laptop")))
                .thenReturn(List.of());
        when(userService.search(eq(user.getOrgId()), eq("laptop"), eq(10)))
                .thenReturn(List.of());

        mvc.perform(get("/api/v1/search").param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray());

        verify(userService).search(user.getOrgId(), "laptop", 10);
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        user.setObjectId("obj-" + roleName.toLowerCase());
        user.setEmail(roleName.toLowerCase() + "@example.com");

        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleName);

        UserRole userRole = new UserRole();
        userRole.setId(UUID.randomUUID());
        userRole.setUser(user);
        userRole.setRole(role);
        user.setUserRoles(Set.of(userRole));

        return user;
    }

    private IncidentSummary sampleIncident() {
        return new IncidentSummary(
                UUID.randomUUID(),
                123L,
                "Broken laptop",
                "NEW",
                "High",
                "Hardware",
                null,
                null,
                "requester",
                null,
                null,
                OffsetDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    private KbSearchResult sampleKb() {
        return new KbSearchResult(UUID.randomUUID(), "KB-1", "How to fix a laptop", "Hardware", 0.5);
    }
}
