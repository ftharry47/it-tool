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
                                              @RequestParam(required = false, defaultValue = "false") boolean mine,
                                              @RequestParam(required = false) OffsetDateTime from,
                                              @RequestParam(required = false) OffsetDateTime to) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsSummary(user.getOrgId(), scopedUserId(auth, user, mine), from, to);
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

    /**
     * Canned SLA-compliance templates: compliance grouped by agent (incident/
     * problem/change assignee, SR fulfillment-task assignees) or location.
     * Query builder can't express this — SLA instances aren't queryable there.
     */
    @GetMapping("/sla-compliance/by-dimension")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> slaComplianceByDimension(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam String entity,
                                                              @RequestParam String dimension) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceByDimension(user.getOrgId(), entity, dimension);
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

    /** Monthly created-vs-closed incident volume (includes legacy imports). */
    @GetMapping("/tickets-monthly")
    public List<Map<String, Object>> ticketsMonthly(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                                    @RequestParam(defaultValue = "12") int months,
                                                    @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsMonthly(user.getOrgId(), months, scopedUserId(auth, user, mine));
    }

    /** Legacy-imported vs natively-created incident totals + per-category split. */
    @GetMapping("/legacy-split")
    public Map<String, Object> legacySplit(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                           @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.legacySplit(user.getOrgId(), scopedUserId(auth, user, mine));
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

    /** CSV/XLSX export of the SLA compliance summary shown on the SLA tab. */
    @GetMapping("/sla-compliance/export")
    public ResponseEntity<byte[]> slaComplianceExport(@AuthenticationPrincipal Jwt jwt,
                                                      Authentication auth,
                                                      @RequestParam(defaultValue = "csv") String format,
                                                      @RequestParam(required = false, defaultValue = "false") boolean mine)
            throws java.io.IOException {
        AppUser user = userService.syncFromJwt(jwt);
        List<Map<String, Object>> rows = List.of(
                reportingService.slaCompliance(user.getOrgId(), scopedUserId(auth, user, mine)));
        return download("sla-compliance", format, rows);
    }

    /** CSV/XLSX export of the SLA-by-priority breakdown. */
    @GetMapping("/sla-by-priority/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> slaByPriorityExport(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "csv") String format)
            throws java.io.IOException {
        AppUser user = userService.syncFromJwt(jwt);
        return download("sla-by-priority", format,
                reportingService.slaComplianceByPriority(user.getOrgId()));
    }

    /**
     * Full Detail Export: Incidents + Service Requests in one union table
     * (type discriminator column), ranged on createdAt. format=json previews,
     * csv/xlsx download.
     */
    @GetMapping("/full-detail-export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> fullDetailExport(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam OffsetDateTime from,
                                              @RequestParam OffsetDateTime to,
                                              @RequestParam(defaultValue = "json") String format)
            throws java.io.IOException {
        AppUser user = userService.syncFromJwt(jwt);
        Map<String, List<Map<String, Object>>> sheets = reportingService.fullDetailExport(
                user.getOrgId(), from, to);
        if ("json".equals(format)) {
            return ResponseEntity.ok(sheets);
        }
        if ("xlsx".equals(format)) {
            return ResponseEntity.ok()
                    .header("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"full-detail-export.xlsx\"")
                    .body(toXlsx(sheets));
        }
        // CSV has no sheets — deliver one ZIP containing one CSV per entity.
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bos)) {
            for (Map.Entry<String, List<Map<String, Object>>> e : sheets.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(e.getKey() + ".csv"));
                zip.write(toCsv(e.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"full-detail-export.zip\"")
                .body(bos.toByteArray());
    }

    /** Per-location ops summary: opened / worked-on / pending / resolved. */
    @GetMapping("/location-dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Map<String, Object>> locationDashboard(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam OffsetDateTime from,
                                                       @RequestParam OffsetDateTime to) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.locationDashboard(user.getOrgId(), from, to);
    }

    /** Per-tier member workload detail — includes zero-work members. */
    @GetMapping("/agent-workload-detail")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Map<String, Object>> agentWorkloadDetail(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(required = false) OffsetDateTime from,
                                                         @RequestParam(required = false) OffsetDateTime to) {
        AppUser user = userService.syncFromJwt(jwt);
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        return reportingService.agentWorkloadDetail(user.getOrgId(), effectiveFrom, effectiveTo);
    }

    /** Monthly SLA compliance split by ticket priority. */
    @GetMapping("/sla-trend/by-priority")
    public List<Map<String, Object>> slaTrendByPriority(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(defaultValue = "12") int months) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaComplianceMonthlyByPriority(user.getOrgId(), months);
    }

    private static ResponseEntity<byte[]> download(String name, String format,
                                                   List<Map<String, Object>> rows)
            throws java.io.IOException {
        String filename = name + "." + ("xlsx".equals(format) ? "xlsx" : "csv");
        if ("xlsx".equals(format)) {
            return ResponseEntity.ok()
                    .header("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(toXlsx(name, rows));
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(toCsv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    /**
     * Drill-down behind "Workload per Agent": the agent's open tickets across
     * all four entity types. Admin-only — an agent's own queue is already
     * visible via their own list pages.
     */
    @GetMapping("/agent-queue")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> agentQueue(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam UUID agentId) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.agentQueue(user.getOrgId(), agentId);
    }

    /**
     * Full worked-ticket list for one or more agents with date/type/status
     * filters. Non-admin callers are scoped to themselves; ADMIN/SUPER_ADMIN
     * may pass repeated/comma-separated agentId params to combine agents.
     */
    @GetMapping("/agent-performance/tickets")
    public List<Map<String, Object>> agentPerformanceTickets(@AuthenticationPrincipal Jwt jwt,
                                                             Authentication auth,
                                                             @RequestParam(required = false) OffsetDateTime from,
                                                             @RequestParam(required = false) OffsetDateTime to,
                                                             @RequestParam(required = false) String entityType,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) List<UUID> agentId) {
        AppUser user = userService.syncFromJwt(jwt);
        List<UUID> targets = (agentId != null && !agentId.isEmpty() && isAdmin(auth))
                ? agentId
                : List.of(user.getId());
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now().minusMonths(3);
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        return reportingService.agentPerformanceTickets(user.getOrgId(), targets,
                effectiveFrom, effectiveTo, entityType, status);
    }

    /** Real-time service-request operations snapshot for admins. */
    @GetMapping("/service-request-ops")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> serviceRequestOps(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.serviceRequestOps(user.getOrgId());
    }

    /** Admin triage: items needing attention right now across all ticket types. */
    @GetMapping("/needs-attention")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> needsAttention(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.needsAttention(user.getOrgId());
    }

    /**
     * Same ad-hoc query, rendered as an XLSX workbook download.
     * format=xlsx triggers the export; anything else returns JSON as before.
     */
    @PostMapping(value = "/query", params = "format=xlsx", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> adHocQueryXlsx(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                                 @RequestBody AdHocQueryRequest request,
                                                 @RequestParam(required = false, defaultValue = "false") boolean mine)
            throws java.io.IOException {
        AppUser user = userService.syncFromJwt(jwt);
        AdHocQueryResponse data = reportingService.adHocQuery(user.getOrgId(), request, scopedUserId(auth, user, mine));

        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(
                    data.entity() != null ? data.entity() : "export");
            java.util.List<String> headers = data.rows().stream()
                    .flatMap(r -> r.keySet().stream())
                    .distinct()
                    .toList();
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                head.createCell(c).setCellValue(headers.get(c));
            }
            int r = 1;
            for (Map<String, Object> row : data.rows()) {
                org.apache.poi.ss.usermodel.Row excelRow = sheet.createRow(r++);
                for (int c = 0; c < headers.size(); c++) {
                    Object v = row.get(headers.get(c));
                    excelRow.createCell(c).setCellValue(v != null ? String.valueOf(v) : "");
                }
            }
            wb.write(bos);
            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=\"" + data.entity() + "-export.xlsx\"")
                    .body(bos.toByteArray());
        }
    }

    /**
     * Row-level data export for the Data Export page — full ticket/SLA rows
     * with resolved display names, as CSV or XLSX. Admin-only.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam String entity,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) OffsetDateTime from,
                                         @RequestParam(required = false) OffsetDateTime to,
                                         @RequestParam(required = false) String status)
            throws java.io.IOException {
        AppUser user = userService.syncFromJwt(jwt);
        List<Map<String, Object>> rows = reportingService.exportRows(
                user.getOrgId(), entity, from, to, status);
        String filename = entity + "-export." + ("xlsx".equals(format) ? "xlsx" : "csv");
        if ("xlsx".equals(format)) {
            return ResponseEntity.ok()
                    .header("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(toXlsx(entity, rows));
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(toCsv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String toCsv(List<Map<String, Object>> rows) {
        List<String> headers = rows.stream()
                .flatMap(r -> r.keySet().stream())
                .distinct()
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(ReportingController::csvCell).toList()))
                .append('\n');
        for (Map<String, Object> r : rows) {
            sb.append(String.join(",", headers.stream()
                            .map(h -> csvCell(r.get(h) != null ? String.valueOf(r.get(h)) : ""))
                            .toList()))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String csvCell(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private static byte[] toXlsx(String sheetName, List<Map<String, Object>> rows)
            throws java.io.IOException {
        return toXlsx(Map.of(sheetName, rows));
    }

    /** One workbook, one sheet per map entry (e.g. Incidents / Service Requests). */
    private static byte[] toXlsx(Map<String, List<Map<String, Object>>> sheets)
            throws java.io.IOException {
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            for (Map.Entry<String, List<Map<String, Object>>> e : sheets.entrySet()) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(e.getKey());
                java.util.List<String> headers = e.getValue().stream()
                        .flatMap(r -> r.keySet().stream())
                        .distinct()
                        .toList();
                org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
                for (int c = 0; c < headers.size(); c++) {
                    head.createCell(c).setCellValue(headers.get(c));
                }
                int r = 1;
                for (Map<String, Object> row : e.getValue()) {
                    org.apache.poi.ss.usermodel.Row excelRow = sheet.createRow(r++);
                    for (int c = 0; c < headers.size(); c++) {
                        Object v = row.get(headers.get(c));
                        excelRow.createCell(c).setCellValue(v != null ? String.valueOf(v) : "");
                    }
                }
            }
            wb.write(bos);
            return bos.toByteArray();
        }
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
