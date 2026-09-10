package com.alignedcardio.itsm.api.location;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.LocationService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final UserService userService;
    private final LocationService locationService;

    public LocationController(UserService userService, LocationService locationService) {
        this.userService = userService;
        this.locationService = locationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LocationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return locationService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LocationResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody LocationRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        LocationResponse response = locationService.create(user, user.getOrgId(), request);
        return ResponseEntity.created(URI.create("/api/v1/locations/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public LocationResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody LocationRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return locationService.update(user, user.getOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        locationService.delete(user.getOrgId(), id);
        return ResponseEntity.noContent().build();
    }
}
