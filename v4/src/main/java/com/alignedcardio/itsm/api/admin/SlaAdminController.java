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

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class SlaAdminController {

    private final UserService userService;
    private final SlaAdminService slaAdminService;

    public SlaAdminController(UserService userService, SlaAdminService slaAdminService) {
        this.userService = userService;
        this.slaAdminService = slaAdminService;
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyResponse> listPolicies(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.listPolicies(user.getOrgId());
    }

    @PostMapping("/sla-policies")
    public SlaPolicyResponse createPolicy(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody SlaPolicyRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.createPolicy(user.getOrgId(), user.getId(), request);
    }

    @PutMapping("/sla-policies/{id}")
    public SlaPolicyResponse updatePolicy(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody SlaPolicyRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.updatePolicy(user.getOrgId(), id, user.getId(), request);
    }

    @DeleteMapping("/sla-policies/{id}")
    public void deletePolicy(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id) {
        var user = userService.syncFromJwt(jwt);
        slaAdminService.deletePolicy(user.getOrgId(), id);
    }

    @GetMapping("/business-calendars")
    public List<BusinessCalendar> listCalendars(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.listCalendars(user.getOrgId());
    }

    @PostMapping("/business-calendars")
    public BusinessCalendar createCalendar(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody BusinessCalendarRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.createCalendar(user.getOrgId(), user.getId(), request);
    }

    @PutMapping("/business-calendars/{id}")
    public BusinessCalendar updateCalendar(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody BusinessCalendarRequest request) {
        var user = userService.syncFromJwt(jwt);
        return slaAdminService.updateCalendar(user.getOrgId(), id, user.getId(), request);
    }

    @DeleteMapping("/business-calendars/{id}")
    public void deleteCalendar(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id) {
        var user = userService.syncFromJwt(jwt);
        slaAdminService.deleteCalendar(user.getOrgId(), id);
    }
}
