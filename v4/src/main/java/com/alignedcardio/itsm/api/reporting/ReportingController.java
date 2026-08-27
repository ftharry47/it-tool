package com.alignedcardio.itsm.api.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.SavedReport;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.api.reporting.ReportMetadataResponse;
import com.alignedcardio.itsm.service.reporting.AdHocQueryRequest;
import com.alignedcardio.itsm.service.reporting.AdHocQueryResponse;
import com.alignedcardio.itsm.service.reporting.ReportingService;
import com.alignedcardio.itsm.service.reporting.SavedReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
public class ReportingController {

    private final ReportingService reportingService;
    private final SavedReportService savedReportService;
    private final UserService userService;

    public ReportingController(ReportingService reportingService,
                               SavedReportService savedReportService,
                               UserService userService) {
        this.reportingService = reportingService;
        this.savedReportService = savedReportService;
        this.userService = userService;
    }

    @GetMapping("/metadata")
    public ReportMetadataResponse metadata(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.getMetadata();
    }

    @GetMapping("/tickets-summary")
    public Map<String, Object> ticketsSummary(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.ticketsSummary(user.getOrgId());
    }

    @GetMapping("/sla-compliance")
    public Map<String, Object> slaCompliance(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.slaCompliance(user.getOrgId());
    }

    @GetMapping("/agent-workload")
    public List<Map<String, Object>> agentWorkload(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.agentWorkload(user.getOrgId());
    }

    @GetMapping("/sprint-velocity")
    public List<Map<String, Object>> sprintVelocity(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.sprintVelocity(user.getOrgId());
    }

    @PostMapping("/query")
    public AdHocQueryResponse adHocQuery(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody AdHocQueryRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return reportingService.adHocQuery(user.getOrgId(), request);
    }

    @GetMapping("/saved")
    public List<SavedReport> listSaved(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return savedReportService.list(user.getOrgId());
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
