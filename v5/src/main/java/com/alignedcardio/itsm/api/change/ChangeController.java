package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.service.ChangeService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/changes")
public class ChangeController {

    private final UserService userService;
    private final ChangeService changeService;

    public ChangeController(UserService userService, ChangeService changeService) {
        this.userService = userService;
        this.changeService = changeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<ChangeResponse> list(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(defaultValue = "false") boolean mine,
                                   @RequestParam(required = false, defaultValue = "false") boolean showDeleted) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.list(user.getOrgId(), mine ? user.getId() : null, showDeleted);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse create(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ChangeCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.create(user, user.getOrgId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse get(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse update(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody ChangeUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.update(user, user.getOrgId(), id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID id,
                                       @RequestParam ChangeRequest.Status status) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.updateStatus(user, user.getOrgId(), id, status);
    }

    @PostMapping("/{id}/submit-for-approval")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse submitForApproval(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.submitForApproval(user, user.getOrgId(), id);
    }

    @PostMapping("/{id}/approvals")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse addApproval(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody ChangeApprovalRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.addApproval(user, user.getOrgId(), id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse approve(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @RequestParam int sequenceOrder,
                                  @RequestParam(required = false) String comment) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.approve(user, user.getOrgId(), id, sequenceOrder, comment);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeResponse reject(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id,
                                 @RequestParam int sequenceOrder,
                                 @RequestParam(required = false) String comment) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.reject(user, user.getOrgId(), id, sequenceOrder, comment);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ChangeCalendarResponse calendar(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.getCalendar(user.getOrgId(), from, to);
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<com.alignedcardio.itsm.api.auth.AuditLogResponse> listActivity(@AuthenticationPrincipal Jwt jwt,
                                                                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return changeService.listActivity(user.getOrgId(), id);
    }
}
