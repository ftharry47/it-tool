package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.SavedReport;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.SavedReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPerformanceReportJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock private AgentPerformanceService performanceService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private SavedReportRepository savedReportRepository;

    private AgentPerformanceReportJob job;

    @BeforeEach
    void setup() {
        job = new AgentPerformanceReportJob(performanceService, appUserRepository,
                savedReportRepository, new ObjectMapper());
    }

    private AppUser user(String name) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setOrgId(ORG_ID);
        u.setDisplayName(name);
        return u;
    }

    private AgentPerformanceService.AgentPerformanceReport report(AppUser agent, String period) {
        return new AgentPerformanceService.AgentPerformanceReport(
                agent.getId(), agent.getDisplayName(), period,
                12, 10, 83.3, 95.0, 10, 0, 0, 240, 0, 1, 8.3, 95, "A", java.util.Map.of());
    }

    @Test
    void generatesSavedReportForEachAgentWhoHandledTickets() {
        AppUser agent = user("Agent One");
        AppUser superAdmin = user("Super Admin"); // also handled tickets
        YearMonth lastMonth = YearMonth.now().minusMonths(1);

        when(performanceService.forAllAgents(ORG_ID, lastMonth))
                .thenReturn(List.of(report(agent, lastMonth.toString()), report(superAdmin, lastMonth.toString())));
        when(appUserRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(appUserRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        int generated = job.generateForOrg(ORG_ID, lastMonth);

        assertEquals(2, generated);
        ArgumentCaptor<SavedReport> captor = ArgumentCaptor.forClass(SavedReport.class);
        verify(savedReportRepository, times(2)).save(captor.capture());
        List<SavedReport> saved = captor.getAllValues();

        // Both reports are AGENT_PERFORMANCE type, owner-scoped, with a JSON payload.
        for (SavedReport r : saved) {
            assertEquals("AGENT_PERFORMANCE", r.getReportType());
            assertEquals("AGENT_PERFORMANCE", r.getEntity());
            assertNotNull(r.getOwnerUserId());
            assertNotNull(r.getPayload());
            assertTrue(r.getPayload().contains("\"score\":95"));
            assertTrue(r.getName().contains(lastMonth.toString()));
        }
        // The SUPER_ADMIN who handled tickets gets their own report too.
        assertTrue(saved.stream().anyMatch(r -> superAdmin.getId().equals(r.getOwnerUserId())));
        assertTrue(saved.stream().anyMatch(r -> agent.getId().equals(r.getOwnerUserId())));
    }

    @Test
    void skipsAgentsWithNoReports() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        when(performanceService.forAllAgents(ORG_ID, lastMonth)).thenReturn(List.of());

        int generated = job.generateForOrg(ORG_ID, lastMonth);

        assertEquals(0, generated);
        verify(savedReportRepository, never()).save(any());
    }
}
