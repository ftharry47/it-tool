package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.service.PurgeDeletedService;
import com.alignedcardio.itsm.service.PurgeDeletedService.PurgeResult;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/maintenance")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class MaintenanceController {

    private final UserService userService;
    private final PurgeDeletedService purgeDeletedService;

    public MaintenanceController(UserService userService, PurgeDeletedService purgeDeletedService) {
        this.userService = userService;
        this.purgeDeletedService = purgeDeletedService;
    }

    /**
     * Permanently hard-deletes all soft-deleted Incidents and Service Requests,
     * their FK children, polymorphic audit/notification/time-entry rows, and
     * on-disk attachment files. Pass dryRun=true to preview per-table counts.
     */
    @PostMapping("/purge-deleted")
    public PurgeResult purgeDeleted(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "false") boolean dryRun) {
        var user = userService.syncFromJwt(jwt);
        return purgeDeletedService.purge(user.getOrgId(), dryRun);
    }
}
