package com.alignedcardio.itsm.api.automation;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.repository.AutomationRunLogRepository;
import com.alignedcardio.itsm.service.AutomationRuleService;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.automation.AutomationRuleTestService;
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
@RequestMapping("/api/v1/automation/rules")
public class AutomationRuleController {

    private final AutomationRuleService ruleService;
    private final AutomationRuleTestService ruleTestService;
    private final UserService userService;
    private final AutomationRunLogRepository runLogRepository;

    public AutomationRuleController(AutomationRuleService ruleService,
                                    AutomationRuleTestService ruleTestService,
                                    UserService userService,
                                    AutomationRunLogRepository runLogRepository) {
        this.ruleService = ruleService;
        this.ruleTestService = ruleTestService;
        this.userService = userService;
        this.runLogRepository = runLogRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<AutomationRuleResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AutomationRuleCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        AutomationRuleResponse response = ruleService.create(
                user.getOrgId(), user.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/automation/rules/" + response.id()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<AutomationRuleResponse>> list(
            @AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(ruleService.list(user.getOrgId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<AutomationRuleResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(ruleService.get(user.getOrgId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<AutomationRuleResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody @Valid AutomationRuleUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(ruleService.update(
                user.getOrgId(), user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        ruleService.delete(user.getOrgId(), user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<AutomationRuleTestResponse> test(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody @Valid AutomationRuleTestRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.ok(ruleTestService.test(user.getOrgId(), id, request));
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<AutomationRunLogResponse>> runs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        List<AutomationRunLogResponse> responses = runLogRepository
                .findByOrgIdAndRuleIdOrderByCreatedAtDesc(user.getOrgId(), id)
                .stream()
                .map(this::toRunLogResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private AutomationRunLogResponse toRunLogResponse(com.alignedcardio.itsm.entity.AutomationRunLog log) {
        return new AutomationRunLogResponse(
                log.getId(),
                log.getRule().getId(),
                log.getEntityType(),
                log.getEntityId().toString(),
                log.getTriggeredEvent(),
                log.getStatus(),
                log.getOutput(),
                log.getError(),
                log.getExecutedAt()
        );
    }
}
