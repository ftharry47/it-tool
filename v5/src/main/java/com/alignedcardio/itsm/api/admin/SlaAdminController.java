package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.service.SlaAdminService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * SLA policies remain admin-manageable; business calendars are system-level
 * configuration restricted to SUPER_ADMIN (same boundary as the other admin
 * sections). Read access for agents viewing SLA details is unchanged — they
 * were never granted here.
 */
@RestController
@RequestMapping("/api/v1")
public class SlaAdminController {

    private final UserService userService;
    private final SlaAdminService slaAdminService;

    public SlaAdminController(UserService userService, SlaAdminService slaAdminService) {
        this.userService = userService;
        this.slaAdminService = slaAdminService;
    }

    @GetMapping("/sla-policies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<SlaPolicyResponse> listPolicies(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.listPolicies(user.getOrgId());
    }

    @PostMapping("/sla-policies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SlaPolicyResponse createPolicy(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody SlaPolicyRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.createPolicy(user.getOrgId(), user.getId(), request);
    }

    @PutMapping("/sla-policies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public SlaPolicyResponse updatePolicy(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody SlaPolicyRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.updatePolicy(user.getOrgId(), id, user.getId(), request);
    }

    @DeleteMapping("/sla-policies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public void deletePolicy(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id) {
        var user = userService.syncFromJwt(jwt);
        slaAdminService.deletePolicy(user.getOrgId(), id, user.getId());
    }

    @GetMapping("/business-calendars")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<BusinessCalendar> listCalendars(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.listCalendars(user.getOrgId());
    }

    @PostMapping("/business-calendars")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public BusinessCalendar createCalendar(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody BusinessCalendarRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.createCalendar(user.getOrgId(), user.getId(), request);
    }

    @PutMapping("/business-calendars/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public BusinessCalendar updateCalendar(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody BusinessCalendarRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.updateCalendar(user.getOrgId(), id, user.getId(), request);
    }

    @DeleteMapping("/business-calendars/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteCalendar(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id) {
        var user = userService.syncFromJwt(jwt);
        slaAdminService.deleteCalendar(user.getOrgId(), id);
    }
}
