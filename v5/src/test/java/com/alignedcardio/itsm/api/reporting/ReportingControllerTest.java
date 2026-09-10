package com.alignedcardio.itsm.api.reporting;

import com.alignedcardio.itsm.config.SecurityConfig;
import com.alignedcardio.itsm.entity.AppUser;
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
}
