package com.alignedcardio.itsm.api.catalog;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.ServiceCatalogService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog-items")
public class CatalogItemController {

    private final UserService userService;
    private final ServiceCatalogService catalogService;

    public CatalogItemController(UserService userService, ServiceCatalogService catalogService) {
        this.userService = userService;
        this.catalogService = catalogService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CatalogItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return catalogService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CatalogItemResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody CatalogItemCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return catalogService.create(user, user.getOrgId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CatalogItemResponse get(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return catalogService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CatalogItemResponse update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody CatalogItemCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return catalogService.update(user, user.getOrgId(), id, request);
    }
}
