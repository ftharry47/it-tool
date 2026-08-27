package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.AuditLogService;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final UserService userService;

    public AuditLogController(AuditLogService auditLogService, UserService userService) {
        this.auditLogService = auditLogService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(auditLogService.list(user.getOrgId(), entityType, from, to, page));
    }
}
