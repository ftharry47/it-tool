package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.reporting.ReportingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SUPER_ADMIN-only configuration audit — surfaces silently-broken setup
 * (empty support-tier teams, unresolvable approval routing, SLA policies
 * with no escalation tiers) before it produces runtime failures.
 */
@RestController
@RequestMapping("/api/v1/admin/config-health")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ConfigHealthController {

    private final UserService userService;
    private final ReportingService reportingService;

    public ConfigHealthController(UserService userService, ReportingService reportingService) {
        this.userService = userService;
        this.reportingService = reportingService;
    }

    @GetMapping
    public Map<String, Object> configHealth(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.syncFromJwt(jwt);
        return reportingService.configHealth(user.getOrgId());
    }
}
