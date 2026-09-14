package com.alignedcardio.itsm.api.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.SavedReport;
import com.alignedcardio.itsm.service.SlaAdminService;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.reporting.AdHocQueryRequest;
import com.alignedcardio.itsm.service.reporting.AdHocQueryResponse;
import com.alignedcardio.itsm.service.reporting.AgentPerformanceService;
import com.alignedcardio.itsm.service.reporting.ReportingService;
import com.alignedcardio.itsm.service.reporting.SavedReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
public class ReportingController {

    private final ReportingService reportingService;
    private final SavedReportService savedReportService;
    private final AgentPerformanceService agentPerformanceService;
    private final UserService userService;
    private final SlaAdminService slaAdminService;

    public ReportingController(ReportingService reportingService,
                               SavedReportService savedReportService,
                               AgentPerformanceService agentPerformanceService,
                               UserService userService,
                               SlaAdminService slaAdminService) {
        this.reportingService = reportingService;
        this.savedReportService = savedReportService;
        this.agentPerformanceService = agentPerformanceService;
        this.userService = userService;
        this.slaAdminService = slaAdminService;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SUPER_ADMIN"));
    }

    /**
     * Broken-access-control guard: AGENT/TEAM_LEAD are always scoped to their own
     * work — the client-supplied {@code mine} flag is advisory only for
     * ADMIN/SUPER_ADMIN, who get org-wide data by default.
     */
    private UUID scopedUserId(Authentication auth, AppUser user, boolean mine) {
        return isAdmin(auth) ? (mine ? user.getId() : null) : user.getId();
    }

    @GetMapping("/tickets-by-location")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<TicketsByLocationResponse> ticketsByLocation(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(required = false, defaultValue = "OPEN") String status,
                                                              @RequestParam(required = false) OffsetDateTime from,
                                                              @RequestParam(required = false) OffsetDateTime to) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsByLocation(user.getOrgId(), status, from, to);
    }

    @GetMapping("/metadata")
    public ReportMetadataResponse metadata(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.getMetadata();
    }

    @GetMapping("/tickets-summary")
    public Map<String, Object> ticketsSummary(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                              @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsSummary(user.getOrgId(), scopedUserId(auth, user, mine));
    }

    @GetMapping("/sla-compliance")
    public Map<String, Object> slaCompliance(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                             @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaCompliance(user.getOrgId(), scopedUserId(auth, user, mine));
    }

    /** Admin view: org-wide compliance plus per-team and per-agent breakdowns. */
    @GetMapping("/sla-compliance/breakdown")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> slaComplianceBreakdown(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceBreakdown(user.getOrgId());
    }

    /** Agent view: SLA policies governing the caller's own work. */
    @GetMapping("/my-sla-targets")
    public List<Map<String, Object>> mySlaTargets(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return slaAdminService.mySlaTargets(user);
    }

    @GetMapping("/priority-breakdown")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdHocQueryResponse priorityBreakdown(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.priorityBreakdown(user.getOrgId());
    }

    @GetMapping("/tickets-trend")
    public List<Map<String, Object>> ticketsTrend(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                                    @RequestParam(defaultValue = "30") int days,
                                                    @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsTrend(user.getOrgId(), days, scopedUserId(auth, user, mine));
    }

    @GetMapping("/sla-trend")
    public List<Map<String, Object>> slaTrend(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                              @RequestParam(defaultValue = "30") int days,
                                              @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceTrend(user.getOrgId(), days, scopedUserId(auth, user, mine));
    }

    @GetMapping("/sla-trend/monthly")
    public List<Map<String, Object>> slaTrendMonthly(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                                     @RequestParam(defaultValue = "12") int months,
                                                     @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceMonthly(user.getOrgId(), months, scopedUserId(auth, user, mine));
    }

    @GetMapping("/sla-trend/overall")
    public Map<String, Object> slaTrendOverall(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                               @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceOverall(user.getOrgId(), scopedUserId(auth, user, mine));
    }

    @GetMapping("/agent-workload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> agentWorkload(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.agentWorkload(user.getOrgId());
    }

    @GetMapping("/sprint-velocity")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> sprintVelocity(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.sprintVelocity(user.getOrgId());
    }

    @GetMapping("/incidents-by-category")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> incidentsByCategory(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.incidentsByCategory(user.getOrgId());
    }

    @GetMapping("/requests-by-catalog")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> requestsByCatalog(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.requestsByCatalogItem(user.getOrgId());
    }

    @GetMapping("/sla-by-priority")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> slaByPriority(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceByPriority(user.getOrgId());
    }

    @GetMapping("/pending-approvals-backlog")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> pendingApprovalsBacklog(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.pendingApprovalsBacklog(user.getOrgId());
    }

    @PostMapping("/query")
    public AdHocQueryResponse adHocQuery(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                         @RequestBody AdHocQueryRequest request,
                                         @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.adHocQuery(user.getOrgId(), request, scopedUserId(auth, user, mine));
    }

    /** Live current-month performance metrics for the calling agent. */
    @GetMapping("/agent-performance/me")
    public Map<String, Object> myPerformance(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("report", agentPerformanceService.currentMonth(user));
        out.put("rollingSlaCompliancePct", agentPerformanceService.rollingSlaCompliance(user));
        return out;
    }

    /** All agents' generated performance reports — SUPER_ADMIN aggregate view. */
    @GetMapping("/agent-performance/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SavedReport> allAgentPerformance(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.listByType(user.getOrgId(), "AGENT_PERFORMANCE");
    }

    @GetMapping("/saved")
    public List<SavedReport> listSaved(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.listForUser(user.getOrgId(), user.getId());
    }

    @PostMapping("/saved")
    public SavedReport createSaved(@AuthenticationPrincipal Jwt jwt,
                                   @RequestBody SavedReportCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.create(user, request.name(), request.entity(),
                request.filters(), request.groupBy(), request.dateRange());
    }

    @GetMapping("/saved/{id}")
    public ResponseEntity<SavedReport> getSaved(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.get(id, user.getOrgId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/saved/{id}")
    public ResponseEntity<SavedReport> updateSaved(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID id,
                                                   @RequestBody Map<String, Object> request) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.update(id, user.getOrgId(), request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/saved/{id}")
    public ResponseEntity<Void> deleteSaved(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.delete(id, user.getOrgId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
