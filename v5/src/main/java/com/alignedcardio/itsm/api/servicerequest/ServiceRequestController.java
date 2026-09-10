package com.alignedcardio.itsm.api.servicerequest;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.service.ServiceRequestCommentService;
import com.alignedcardio.itsm.service.ServiceRequestService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final UserService userService;
    private final ServiceRequestService serviceRequestService;
    private final ServiceRequestCommentService commentService;

    public ServiceRequestController(UserService userService, ServiceRequestService serviceRequestService,
                                    ServiceRequestCommentService commentService) {
        this.userService = userService;
        this.serviceRequestService = serviceRequestService;
        this.commentService = commentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ServiceRequestResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse create(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody ServiceRequestCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.create(user, user.getOrgId(), request);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<ServiceRequestResponse> myRequests(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.listMine(user);
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public List<MyTaskResponse> myTasks(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.myTasks(user);
    }

    @GetMapping("/approvals/mine")
    @PreAuthorize("isAuthenticated()")
    public List<ServiceRequestResponse> myApprovals(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.listPendingApprovals(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse get(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.get(user, user.getOrgId(), id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse submit(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.submit(user, user.getOrgId(), id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse approve(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody ApprovalRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        if (!request.approve()) {
            throw new IllegalStateException("Use /{id}/reject to reject a request");
        }
        return serviceRequestService.decide(user, user.getOrgId(), id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse reject(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody ApprovalRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        if (request.approve()) {
            throw new IllegalStateException("Use /{id}/approve to approve a request");
        }
        return serviceRequestService.decide(user, user.getOrgId(), id, request);
    }

    @PostMapping("/{id}/send-reminder")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse sendReminder(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @RequestBody Map<String, String> body) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.sendReminder(user, user.getOrgId(), id, body.get("message"));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("isAuthenticated()")
    public List<ServiceRequestActivityResponse> activity(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.getActivity(user, user.getOrgId(), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @RequestParam ServiceRequest.Status status) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.updateStatus(user, user.getOrgId(), id, status);
    }

    @PatchMapping("/{id}/tasks/{taskId}/delivery-date")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse setDeliveryDate(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID id,
                                                  @PathVariable UUID taskId,
                                                  @RequestBody Map<String, String> body) {
        AppUser user = userService.syncFromJwt(jwt);
        java.time.LocalDate date = body.get("expectedDeliveryDate") != null
                ? java.time.LocalDate.parse(body.get("expectedDeliveryDate"))
                : null;
        return serviceRequestService.setDeliveryDate(user, user.getOrgId(), id, taskId, date);
    }

    @PostMapping("/{id}/tasks/{taskId}/ordered")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse markOrdered(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id,
                                              @PathVariable UUID taskId,
                                              @RequestBody(required = false) Map<String, String> body) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.markOrdered(
                user, user.getOrgId(), id, taskId,
                body == null ? null : body.get("orderId"),
                body == null ? null : body.get("vendor"));
    }

    @PostMapping("/{id}/tasks/{taskId}/deliver")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse markDelivered(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID id,
                                                @PathVariable UUID taskId) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.markDelivered(user, user.getOrgId(), id, taskId);
    }

    @PostMapping("/{id}/tasks/{taskId}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ServiceRequestResponse assignTask(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID id,
                                             @PathVariable UUID taskId,
                                             @RequestBody Map<String, String> body) {
        AppUser user = userService.syncFromJwt(jwt);
        UUID assigneeId = body.get("assigneeId") != null
                ? UUID.fromString(body.get("assigneeId"))
                : null;
        if (assigneeId == null) {
            throw new IllegalStateException("assigneeId is required");
        }
        return serviceRequestService.assignTask(user, user.getOrgId(), id, taskId, assigneeId);
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public List<ServiceRequestCommentResponse> listComments(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return commentService.listComments(user.getOrgId(), id, user);
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestCommentResponse addComment(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody CommentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return commentService.addComment(user.getOrgId(), id, user, request);
    }

    @PostMapping("/{id}/tasks/{taskId}/complete")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse completeTask(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @PathVariable UUID taskId,
                                               @RequestBody(required = false) Map<String, String> body) {
        AppUser user = userService.syncFromJwt(jwt);
        String closingNotes = body != null ? body.get("closingNotes") : null;
        return serviceRequestService.completeTask(user, user.getOrgId(), id, taskId, closingNotes);
    }
}
