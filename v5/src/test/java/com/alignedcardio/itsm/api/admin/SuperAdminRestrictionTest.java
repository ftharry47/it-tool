package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.api.automation.AutomationRuleController;
import com.alignedcardio.itsm.api.catalog.CatalogItemController;
import com.alignedcardio.itsm.api.category.CategoryController;
import com.alignedcardio.itsm.api.project.WorkflowController;
import com.alignedcardio.itsm.api.team.TeamController;
import com.alignedcardio.itsm.api.user.UserController;
import com.alignedcardio.itsm.config.GlobalExceptionHandler;
import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.repository.AutomationRunLogRepository;
import com.alignedcardio.itsm.service.*;
import com.alignedcardio.itsm.service.automation.AutomationRuleTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for the SUPER_ADMIN boundary: system-configuration areas reject
 * ADMIN with 403, accept SUPER_ADMIN, while SLA policy management (a shared
 * admin surface) stays open to ADMIN.
 */
@WebMvcTest({UserController.class, TeamController.class, CatalogItemController.class,
        CategoryController.class, AutomationRuleController.class, SlaAdminController.class,
        WorkflowController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SuperAdminRestrictionTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mvc;

    @MockBean private UserService userService;
    @MockBean private GraphUserSyncService graphUserSyncService;
    @MockBean private TeamService teamService;
    @MockBean private ServiceCatalogService catalogService;
    @MockBean private CategoryService categoryService;
    @MockBean private AutomationRuleService ruleService;
    @MockBean private AutomationRuleTestService ruleTestService;
    @MockBean private AutomationRunLogRepository runLogRepository;
    @MockBean private SlaAdminService slaAdminService;
    @MockBean private WorkflowService workflowService;

    @BeforeEach
    void actor() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(BaseEntity.DEFAULT_ORG_ID);
        when(userService.syncFromJwt(any())).thenReturn(user);
    }

    // ---------- Users ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageUsers() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\",\"displayName\":\"X\",\"roleName\":\"AGENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/sync-from-ad")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/{id}/role", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"AGENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanUpdateUsers() throws Exception {
        mvc.perform(patch("/api/v1/users/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());
    }

    // ---------- Support Tiers / Teams ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageTeams() throws Exception {
        mvc.perform(post("/api/v1/teams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teams/{id}/members", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + ID + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/teams/{id}/members/{uid}", ID, ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageTeams() throws Exception {
        when(teamService.create(any(), any(), any())).thenReturn(
                new com.alignedcardio.itsm.api.team.TeamResponse(ID, "T1", null, ID, ID, java.util.List.of()));
        mvc.perform(post("/api/v1/teams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T1\"}"))
                .andExpect(status().isCreated());
    }

    // ---------- Catalog ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageCatalog() throws Exception {
        mvc.perform(post("/api/v1/catalog-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Item\",\"formSchema\":\"{}\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/catalog-items/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Item\",\"formSchema\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageCatalog() throws Exception {
        mvc.perform(post("/api/v1/catalog-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Item\",\"formSchema\":\"{}\"}"))
                .andExpect(status().isOk());
    }

    // ---------- Categories ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageCategories() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"C\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/categories/{id}", ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageCategories() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    // ---------- Automation Rules ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageAutomationRules() throws Exception {
        mvc.perform(get("/api/v1/automation/rules")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/automation/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R\",\"triggerType\":\"T\",\"triggerEntity\":\"INCIDENT\","
                                + "\"triggerConfig\":\"{}\",\"conditions\":\"{}\",\"actions\":\"[]\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/automation/rules/{id}", ID)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/automation/rules/{id}/test", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"samplePayload\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageAutomationRules() throws Exception {
        mvc.perform(get("/api/v1/automation/rules")).andExpect(status().isOk());
    }

    // ---------- Business Calendars vs SLA Policies ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageBusinessCalendars() throws Exception {
        mvc.perform(get("/api/v1/business-calendars")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/business-calendars").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"C\",\"timezone\":\"UTC\",\"workingHours\":\"{}\",\"holidays\":\"[]\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/business-calendars/{id}", ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminStillManagesSlaPolicies() throws Exception {
        // SLA policy admin stays shared — the split must not over-restrict.
        mvc.perform(get("/api/v1/sla-policies")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageBusinessCalendars() throws Exception {
        mvc.perform(get("/api/v1/business-calendars")).andExpect(status().isOk());
    }

    // ---------- Workflows ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotManageWorkflows() throws Exception {
        mvc.perform(post("/api/v1/workflows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"W\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/workflows/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"W\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/workflows/{id}", ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void teamLeadCannotManageWorkflows() throws Exception {
        mvc.perform(post("/api/v1/workflows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"W\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanManageWorkflows() throws Exception {
        mvc.perform(post("/api/v1/workflows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"W\"}"))
                .andExpect(status().isOk());
    }
}
