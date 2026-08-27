package com.alignedcardio.itsm.api.incident;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.service.IncidentAttachmentService;
import com.alignedcardio.itsm.service.IncidentCommentService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
                                        @RequestParam(name = "search", required = false) String query) {
        AppUser user = userService.syncFromJwt(jwt);
        if (query == null || query.isBlank()) {
            return incidentService.list(user.getOrgId());
        }
        return incidentService.search(user.getOrgId(), query);
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody StatusUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.updateStatus(user, user.getOrgId(), id, Incident.Status.valueOf(request.status()));
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
