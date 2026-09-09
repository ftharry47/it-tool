package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.SavedReport;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.SavedReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Runs once at the start of each month. Generates a frozen
 * AGENT_PERFORMANCE SavedReport for every user who handled at least one
 * ticket (incident or fulfillment task) in the month that just ended —
 * including ADMIN/SUPER_ADMIN users who personally handled tickets.
 */
@Component
public class AgentPerformanceReportJob implements Job {

    private final AgentPerformanceService performanceService;
    private final AppUserRepository appUserRepository;
    private final SavedReportRepository savedReportRepository;
    private final ObjectMapper objectMapper;

    public AgentPerformanceReportJob(AgentPerformanceService performanceService,
                                     AppUserRepository appUserRepository,
                                     SavedReportRepository savedReportRepository,
                                     ObjectMapper objectMapper) {
        this.performanceService = performanceService;
        this.appUserRepository = appUserRepository;
        this.savedReportRepository = savedReportRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        // Single-org deployment: derive org from any user row.
        List<AppUser> users = appUserRepository.findAll();
        users.stream().map(AppUser::getOrgId).distinct().forEach(orgId -> generateForOrg(orgId, lastMonth));
    }

    /** Package-visible for tests. Returns the number of reports generated. */
    @Transactional
    public int generateForOrg(UUID orgId, YearMonth month) {
        int generated = 0;
        for (AgentPerformanceService.AgentPerformanceReport report
                : performanceService.forAllAgents(orgId, month)) {
            AppUser agent = appUserRepository.findById(report.agentId()).orElse(null);
            if (agent == null) continue;

            SavedReport saved = new SavedReport();
            saved.setOrgId(orgId);
            saved.setName("Agent Performance — " + agent.getDisplayName() + " — " + report.period());
            saved.setEntity("AGENT_PERFORMANCE");
            saved.setReportType("AGENT_PERFORMANCE");
            saved.setOwnerUserId(agent.getId());
            try {
                saved.setPayload(objectMapper.writeValueAsString(report));
            } catch (Exception e) {
                continue; // skip unserializable report rather than failing the run
            }
            savedReportRepository.save(saved);
            generated++;
        }
        return generated;
    }
}
