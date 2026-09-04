package com.alignedcardio.itsm.api.incident;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.service.IncidentAttachmentService;
import com.alignedcardio.itsm.service.IncidentCommentService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentV1Controller {

    private final UserService userService;
    private final IncidentService incidentService;
    private final IncidentCommentService commentService;
    private final IncidentAttachmentService attachmentService;

    public IncidentV1Controller(UserService userService,
                                IncidentService incidentService,
                                IncidentCommentService commentService,
                                IncidentAttachmentService attachmentService) {
        this.userService = userService;
        this.incidentService = incidentService;
        this.commentService = commentService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<IncidentSummary> search(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(name = "search", required = false) String query,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) UUID assigneeId,
                                        @RequestParam(required = false) Boolean unassigned,
                                        @RequestParam(required = false, defaultValue = "20") int limit) {
        AppUser user = userService.syncFromJwt(jwt);
        if (query != null && !query.isBlank()) {
            return incidentService.search(user.getOrgId(), query);
        }
        if (status != null && !status.isBlank()) {
            List<Incident.Status> statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .map(Incident.Status::valueOf)
                    .toList();
            if (unassigned != null && unassigned) {
                return incidentService.listUnassigned(user.getOrgId(), statuses, limit);
            }
            return incidentService.listFiltered(user.getOrgId(), statuses, assigneeId, limit);
        }
        if (unassigned != null && unassigned) {
            return incidentService.listUnassigned(user.getOrgId(), null, limit);
        }
        if (assigneeId != null) {
            List<Incident.Status> statuses = List.of(
                    Incident.Status.NEW,
                    Incident.Status.IN_PROGRESS,
                    Incident.Status.ON_HOLD,
                    Incident.Status.RESOLVED,
                    Incident.Status.REOPENED);
            return incidentService.listFiltered(user.getOrgId(), statuses, assigneeId, limit);
        }
        return incidentService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public IncidentResponse create(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody IncidentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.create(user, request);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<IncidentSummary> myIncidents(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.listByReporter(user.getOrgId(), user.getId());
    }

    @GetMapping("/priorities")
    @PreAuthorize("isAuthenticated()")
    public List<PriorityOption> priorities(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.priorities(user.getOrgId());
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public List<CategoryOption> categories(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.categories(user.getOrgId());
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public IncidentResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody IncidentUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.superAdminUpdate(user, user.getOrgId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody StatusUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.updateStatus(user, user.getOrgId(), id, Incident.Status.valueOf(request.status()));
    }

    @PostMapping("/{id}/time")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IncidentResponse logTime(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody TimeLogRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.logTime(user, user.getOrgId(), id, request.timeSpentMinutes(), request.description());
    }

    @GetMapping("/{id}/time-entries")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<TimeEntryResponse> listTimeEntries(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.listTimeEntries(user.getOrgId(), id);
    }

    @PatchMapping("/{id}/estimate")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IncidentResponse updateEstimate(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody EstimateUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.setEstimatedMinutes(user, user.getOrgId(), id, request.estimatedMinutes());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IncidentResponse assign(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody AssignRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.assign(user, user.getOrgId(), id, request.assigneeId());
    }

    @GetMapping("/{id}/comments")
    public List<IncidentCommentResponse> listComments(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return commentService.listComments(user.getOrgId(), id, user);
    }

    @PostMapping("/{id}/comments")
    public IncidentCommentResponse addComment(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody CommentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return commentService.addComment(user.getOrgId(), id, user, request);
    }

    @GetMapping("/{id}/attachments")
    public List<IncidentAttachmentResponse> listAttachments(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return attachmentService.listAttachments(user.getOrgId(), id);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> getAttachment(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID id,
                                                  @PathVariable UUID attachmentId) {
        AppUser user = userService.syncFromJwt(jwt);
        Resource resource = attachmentService.getAttachmentResource(user.getOrgId(), id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(resource);
    }

    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    public IncidentAttachmentResponse addAttachment(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID id,
                                                    @RequestParam("file") MultipartFile file) {
        AppUser user = userService.syncFromJwt(jwt);
        return attachmentService.storeAttachment(user.getOrgId(), id, user, file);
    }

    @GetMapping("/{id}/watchers")
    public List<IncidentWatcherResponse> listWatchers(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.listWatchers(user.getOrgId(), id);
    }

    @PostMapping("/{id}/watchers")
    public IncidentWatcherResponse addWatcher(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id,
                                              @RequestParam("userId") UUID userId) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.addWatcher(user.getOrgId(), id, userId);
    }

    @DeleteMapping("/{id}/watchers/{userId}")
    public void removeWatcher(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @PathVariable UUID userId) {
        AppUser user = userService.syncFromJwt(jwt);
        incidentService.removeWatcher(user.getOrgId(), id, userId);
    }

    @GetMapping("/{id}/links")
    public List<IncidentLinkResponse> listLinks(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.listLinks(user.getOrgId(), id);
    }

    @PostMapping("/{id}/links")
    public IncidentLinkResponse addLink(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody LinkCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.addLink(user.getOrgId(), id, request);
    }

    @GetMapping("/{id}/sla")
    public ResponseEntity<SlaInstanceResponse> getSla(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        SlaInstanceResponse response = incidentService.getSla(user.getOrgId(), id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
