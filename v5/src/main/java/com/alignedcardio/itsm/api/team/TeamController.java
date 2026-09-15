package com.alignedcardio.itsm.api.team;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.TeamService;
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
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final UserService userService;

    public TeamController(TeamService teamService, UserService userService) {
        this.teamService = teamService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TeamResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid TeamCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        TeamResponse response = teamService.create(user.getOrgId(), user.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + response.id()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamResponse>> list(
            @AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(teamService.list(user.getOrgId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(teamService.get(user.getOrgId(), id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody @Valid TeamMemberRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        teamService.addMember(user.getOrgId(), id, request.userId(), user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        AppUser user = userService.syncFromJwt(jwt);
        teamService.removeMember(user.getOrgId(), id, userId);
        return ResponseEntity.noContent().build();
    }
}
