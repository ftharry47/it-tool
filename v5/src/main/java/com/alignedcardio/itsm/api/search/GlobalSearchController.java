package com.alignedcardio.itsm.api.search;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.ChangeService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.KnowledgeBaseService;
import com.alignedcardio.itsm.service.ProblemService;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GlobalSearchController {

    private final UserService userService;
    private final IncidentService incidentService;
    private final ProblemService problemService;
    private final ChangeService changeService;
    private final KnowledgeBaseService knowledgeBaseService;

    public GlobalSearchController(UserService userService,
                                  IncidentService incidentService,
                                  ProblemService problemService,
                                  ChangeService changeService,
                                  KnowledgeBaseService knowledgeBaseService) {
        this.userService = userService;
        this.incidentService = incidentService;
        this.problemService = problemService;
        this.changeService = changeService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public GlobalSearchResponse search(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam String q) {
        AppUser user = userService.syncFromJwt(jwt);
        UUID orgId = user.getOrgId();

        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        boolean isEndUser = isEndUserOnly(roles);
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("SUPER_ADMIN");

        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return new GlobalSearchResponse(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        List<com.alignedcardio.itsm.api.incident.IncidentSummary> incidents = isEndUser
                ? incidentService.search(orgId, query, user.getId(), 10)
                : incidentService.search(orgId, query, null, 10);

        List<com.alignedcardio.itsm.api.problem.ProblemResponse> problems = isEndUser
                ? Collections.emptyList()
                : problemService.search(orgId, query, 10);
        List<com.alignedcardio.itsm.api.change.ChangeResponse> changes = isEndUser
                ? Collections.emptyList()
                : changeService.search(orgId, query, 10);
        List<com.alignedcardio.itsm.api.kb.KbSearchResult> kb = knowledgeBaseService.search(orgId, query).stream().limit(10).toList();
        List<com.alignedcardio.itsm.api.user.UserResponse> users = isAdmin
                ? userService.search(orgId, query, 10)
                : Collections.emptyList();

        return new GlobalSearchResponse(incidents, problems, changes, kb, users);
    }

    private boolean isEndUserOnly(List<String> roles) {
        if (!roles.contains("END_USER")) {
            return false;
        }
        return roles.stream().noneMatch(r -> r.equals("ADMIN") || r.equals("SUPER_ADMIN") || r.equals("AGENT") || r.equals("TEAM_LEAD"));
    }
}
