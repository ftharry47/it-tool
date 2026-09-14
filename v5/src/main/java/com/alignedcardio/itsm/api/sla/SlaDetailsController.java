package com.alignedcardio.itsm.api.sla;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.SlaDetailsService;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sla-instances")
@PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
public class SlaDetailsController {

    private final UserService userService;
    private final SlaDetailsService slaDetailsService;

    public SlaDetailsController(UserService userService, SlaDetailsService slaDetailsService) {
        this.userService = userService;
        this.slaDetailsService = slaDetailsService;
    }

    @GetMapping
    public List<SlaInstanceDetailResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<String> breachStatus,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            Authentication auth) {
        AppUser user = userService.syncFromJwt(jwt);
        // Non-admin staff can only ever see their own SLA instances — the
        // client-supplied mine flag is advisory for ADMIN/SUPER_ADMIN only.
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SUPER_ADMIN"));
        return slaDetailsService.list(user.getOrgId(), breachStatus, priority, dateFrom, dateTo, user, isAdmin ? mine : true);
    }
}
