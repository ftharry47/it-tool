package com.alignedcardio.itsm.api.servicerequest;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.service.ServiceRequestService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final UserService userService;
    private final ServiceRequestService serviceRequestService;

    public ServiceRequestController(UserService userService, ServiceRequestService serviceRequestService) {
        this.userService = userService;
        this.serviceRequestService = serviceRequestService;
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

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ServiceRequestResponse get(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.get(user.getOrgId(), id);
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
        return serviceRequestService.decide(user, user.getOrgId(), id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @RequestParam ServiceRequest.Status status) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.updateStatus(user, user.getOrgId(), id, status);
    }

    @PostMapping("/{id}/tasks/{taskId}/complete")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ServiceRequestResponse completeTask(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @PathVariable UUID taskId) {
        AppUser user = userService.syncFromJwt(jwt);
        return serviceRequestService.completeTask(user, user.getOrgId(), id, taskId);
    }
}
