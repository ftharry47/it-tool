package com.alignedcardio.itsm.api;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.SoftDeleteService;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bulk")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class SoftDeleteController {

    private final UserService userService;
    private final SoftDeleteService softDeleteService;

    public SoftDeleteController(UserService userService, SoftDeleteService softDeleteService) {
        this.userService = userService;
        this.softDeleteService = softDeleteService;
    }

    @PostMapping("/{entityType}/soft-delete")
    public ResponseEntity<Void> softDelete(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable String entityType,
                                           @RequestBody List<UUID> ids) {
        AppUser user = userService.syncFromJwt(jwt);
        softDeleteService.softDelete(user, user.getOrgId(), entityType, ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{entityType}/restore")
    public ResponseEntity<Void> restore(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable String entityType,
                                        @RequestBody List<UUID> ids) {
        AppUser user = userService.syncFromJwt(jwt);
        softDeleteService.restore(user, user.getOrgId(), entityType, ids);
        return ResponseEntity.noContent().build();
    }
}
