package com.alignedcardio.itsm.api.kb;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.KbArticle;
import com.alignedcardio.itsm.service.KnowledgeBaseService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeBaseController {

    private final UserService userService;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(UserService userService, KnowledgeBaseService knowledgeBaseService) {
        this.userService = userService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public List<KbArticleSummary> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) KbArticle.Status status) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.list(user.getOrgId(), status);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public KbArticleResponse create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody KbArticleCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.create(user, user.getOrgId(), request);
    }

    @GetMapping("/{id}")
    public KbArticleResponse get(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public KbArticleResponse update(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody KbArticleUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.update(user, user.getOrgId(), id, request);
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("isAuthenticated()")
    public KbArticleResponse feedback(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody KbFeedbackRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.addFeedback(user, user.getOrgId(), id, request);
    }

    @GetMapping("/{id}/versions")
    public List<KbArticleVersionResponse> versions(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.listVersions(user.getOrgId(), id);
    }

    @GetMapping("/search")
    public List<KbSearchResult> search(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam String q) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.search(user.getOrgId(), q);
    }

    @GetMapping("/suggest")
    public List<KbSearchResult> suggest(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam String description) {
        AppUser user = userService.syncFromJwt(jwt);
        return knowledgeBaseService.suggest(user.getOrgId(), description);
    }
}
