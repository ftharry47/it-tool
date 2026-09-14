package com.alignedcardio.itsm.api.reporting;

import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.SlaAdminService;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.reporting.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
@Import(SecurityConfig.class)
class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportingService reportingService;

    @MockBean
    private SavedReportService savedReportService;

    @MockBean
    private AgentPerformanceService agentPerformanceService;

    @MockBean
    private UserService userService;

    @MockBean
    private SlaAdminService slaAdminService;

    private AppUser testUser() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(UUID.randomUUID());
        return user;
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCanAccessTicketsSummary() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);
        when(reportingService.ticketsSummary(user.getOrgId(), null)).thenReturn(
                Map.of("total", 1L, "open", 0L, "inProgress", 0L, "resolvedToday", 0L));

        mockMvc.perform(get("/api/v1/reports/tickets-summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "END_USER")
    void endUserCannotAccessReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets-summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCanRunAdHocQuery() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);

        AdHocQueryResponse response = new AdHocQueryResponse(
                user.getOrgId(), "incident", "status",
                List.of(Map.of("group", "NEW", "count", 5L)));
        when(reportingService.adHocQuery(eq(user.getOrgId()), any(AdHocQueryRequest.class), any()))
                .thenReturn(response);

        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("status", "eq", "NEW")),
                "status",
                new AdHocQueryRequest.DateRange(OffsetDateTime.now().minusMonths(1), OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/reports/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // --- Broken-access-control regression: agents are scoped server-side ---

    @Test
    @WithMockUser(roles = "AGENT")
    void agentIsScopedToOwnDataEvenWithoutMineParam() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);
        when(reportingService.ticketsSummary(eq(user.getOrgId()), eq(user.getId())))
                .thenReturn(Map.of("total", 1L));

        mockMvc.perform(get("/api/v1/reports/tickets-summary"))
                .andExpect(status().isOk());

        verify(reportingService).ticketsSummary(user.getOrgId(), user.getId());
        verify(reportingService, never()).ticketsSummary(any(), isNull());
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void teamLeadIsScopedToOwnDataEvenWithoutMineParam() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);
        when(reportingService.slaCompliance(eq(user.getOrgId()), eq(user.getId())))
                .thenReturn(Map.of("total", 0L));

        mockMvc.perform(get("/api/v1/reports/sla-compliance"))
                .andExpect(status().isOk());

        verify(reportingService).slaCompliance(user.getOrgId(), user.getId());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminStillGetsOrgWideDataWithoutMineParam() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);
        when(reportingService.ticketsSummary(eq(user.getOrgId()), isNull()))
                .thenReturn(Map.of("total", 5L));

        mockMvc.perform(get("/api/v1/reports/tickets-summary"))
                .andExpect(status().isOk());

        verify(reportingService).ticketsSummary(user.getOrgId(), null);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentAdHocQueryIsScopedEvenWithoutMineParam() throws Exception {
        AppUser user = testUser();
        lenient().when(userService.syncFromJwt(any())).thenReturn(user);
        when(reportingService.adHocQuery(eq(user.getOrgId()), any(AdHocQueryRequest.class), eq(user.getId())))
                .thenReturn(new AdHocQueryResponse(user.getOrgId(), "problem", null, List.of()));

        AdHocQueryRequest request = new AdHocQueryRequest("problem", List.of(), null, null);

        mockMvc.perform(post("/api/v1/reports/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(reportingService).adHocQuery(eq(user.getOrgId()), any(AdHocQueryRequest.class), eq(user.getId()));
        verify(reportingService, never()).adHocQuery(any(), any(), isNull());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotPullOrgWideAggregateReports() throws Exception {
        for (String path : List.of(
                "/api/v1/reports/agent-workload",
                "/api/v1/reports/sprint-velocity",
                "/api/v1/reports/incidents-by-category",
                "/api/v1/reports/requests-by-catalog",
                "/api/v1/reports/sla-by-priority",
                "/api/v1/reports/pending-approvals-backlog",
                "/api/v1/reports/tickets-by-location",
                "/api/v1/reports/priority-breakdown",
                "/api/v1/reports/sla-compliance/breakdown")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isForbidden());
        }
    }
}
