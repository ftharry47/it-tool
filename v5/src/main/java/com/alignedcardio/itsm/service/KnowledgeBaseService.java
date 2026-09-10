package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.KbArticle;
import com.alignedcardio.itsm.entity.KbArticleVersion;
import com.alignedcardio.itsm.entity.KbFeedback;
import com.alignedcardio.itsm.repository.KbArticleRepository;
import com.alignedcardio.itsm.repository.KbArticleVersionRepository;
import com.alignedcardio.itsm.repository.KbFeedbackRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final KbArticleRepository kbArticleRepository;
    private final KbArticleVersionRepository kbArticleVersionRepository;
    private final KbFeedbackRepository kbFeedbackRepository;
    private final KnowledgeBaseSearch knowledgeBaseSearch;
    private final EntityManager entityManager;

    public KnowledgeBaseService(KbArticleRepository kbArticleRepository,
                                KbArticleVersionRepository kbArticleVersionRepository,
                                KbFeedbackRepository kbFeedbackRepository,
                                KnowledgeBaseSearch knowledgeBaseSearch,
                                EntityManager entityManager) {
        this.kbArticleRepository = kbArticleRepository;
        this.kbArticleVersionRepository = kbArticleVersionRepository;
        this.kbFeedbackRepository = kbFeedbackRepository;
        this.knowledgeBaseSearch = knowledgeBaseSearch;
        this.entityManager = entityManager;
    }

    @Transactional
    public KbArticleResponse create(AppUser user, UUID orgId, KbArticleCreateRequest request) {
        if (!isKbContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can create KB articles");
        }
        KbArticle article = new KbArticle();
        article.setOrgId(orgId);
        article.setTitle(request.title());
        article.setCategory(request.category());
        article.setBody(request.body());
        article.setStatus(KbArticle.Status.DRAFT);
        article.setAuthor(user);
        article.setCreatedBy(user.getId());
        article.setUpdatedBy(user.getId());
        article.setNumber(generateArticleNumber());

        KbArticle saved = kbArticleRepository.save(article);
        entityManager.flush();
        entityManager.refresh(saved);

        return toResponse(saved, user.getId());
    }

    @Transactional(readOnly = true)
    public List<KbArticleSummary> list(UUID orgId, KbArticle.Status status) {
        List<KbArticle> articles;
        if (status != null) {
            articles = kbArticleRepository.findByOrgIdAndStatusOrderByUpdatedAtDesc(orgId, status);
        } else {
            articles = kbArticleRepository.findByOrgIdOrderByUpdatedAtDesc(orgId);
        }
        return articles.stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public KbArticleResponse get(UUID orgId, UUID id, UUID userId) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));
        article.setViewCount(article.getViewCount() + 1);
        kbArticleRepository.save(article);
        return toResponse(article, userId);
    }

    @Transactional
    public KbArticleResponse update(AppUser user, UUID orgId, UUID id, KbArticleUpdateRequest request) {
        if (!isKbContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can update KB articles");
        }
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));

        KbArticle.Status newStatus = request.status() != null ? request.status() : article.getStatus();
        KbStatusMachine.validate(article.getStatus(), newStatus);

        if (request.status() != null && request.status() != article.getStatus()) {
            KbArticle.Status to = request.status();
            if (to == KbArticle.Status.PENDING_REVIEW) {
                if (!isAuthor(user, article) && !isKbAdmin(user)) {
                    throw new IllegalStateException("Only the author or an ADMIN / SUPER_ADMIN can submit an article for review");
                }
            } else if (to == KbArticle.Status.PUBLISHED
                    || to == KbArticle.Status.ARCHIVED
                    || to == KbArticle.Status.DRAFT) {
                if (!isKbAdmin(user)) {
                    throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can publish, archive, or restore articles");
                }
            }
        }

        // Snapshot only when the live article is currently PUBLISHED and content is changing.
        boolean contentChanged = contentChanged(article, request);
        if (article.getStatus() == KbArticle.Status.PUBLISHED && contentChanged) {
            saveVersion(article, user.getId());
            article.setVersion(article.getVersion() + 1);
        }

        if (contentChanged) {
            if (request.title() != null) article.setTitle(request.title());
            if (request.category() != null) article.setCategory(request.category());
            if (request.body() != null) article.setBody(request.body());
        }

        if (request.status() != null) {
            article.setStatus(request.status());
            if (request.status() == KbArticle.Status.PUBLISHED) {
                article.setPublishedAt(OffsetDateTime.now());
            } else if (request.status() == KbArticle.Status.ARCHIVED) {
                article.setArchivedAt(OffsetDateTime.now());
            }
        }

        article.setUpdatedBy(user.getId());
        article.setUpdatedAt(OffsetDateTime.now());

        return toResponse(kbArticleRepository.save(article), user.getId());
    }

    @Transactional
    public void delete(AppUser user, UUID orgId, UUID id) {
        if (!isKbAdmin(user)) {
            throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can delete KB articles");
        }
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));
        article.setDeletedAt(OffsetDateTime.now());
        article.setUpdatedBy(user.getId());
        kbArticleRepository.save(article);
    }

    @Transactional
    public KbArticleResponse addFeedback(AppUser user, UUID orgId, UUID id, KbFeedbackRequest request) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));

        // One vote per user: re-voting with the same value is a no-op,
        // voting the other way flips the existing vote and adjusts counts.
        KbFeedback feedback = kbFeedbackRepository
                .findByKbArticleIdAndCreatedBy(article.getId(), user.getId())
                .orElse(null);
        if (feedback != null && feedback.isHelpful() == request.helpful()) {
            return toResponse(article, user.getId());
        }
        if (feedback != null) {
            if (feedback.isHelpful()) {
                article.setHelpfulCount(article.getHelpfulCount() - 1);
                article.setNotHelpfulCount(article.getNotHelpfulCount() + 1);
            } else {
                article.setNotHelpfulCount(article.getNotHelpfulCount() - 1);
                article.setHelpfulCount(article.getHelpfulCount() + 1);
            }
            feedback.setHelpful(request.helpful());
            feedback.setComment(request.comment());
            kbFeedbackRepository.save(feedback);
        } else {
            feedback = new KbFeedback();
            feedback.setKbArticle(article);
            feedback.setHelpful(request.helpful());
            feedback.setComment(request.comment());
            feedback.setCreatedBy(user.getId());
            feedback.setOrgId(orgId);
            kbFeedbackRepository.save(feedback);

            if (request.helpful()) {
                article.setHelpfulCount(article.getHelpfulCount() + 1);
            } else {
                article.setNotHelpfulCount(article.getNotHelpfulCount() + 1);
            }
        }

        return toResponse(kbArticleRepository.save(article), user.getId());
    }

    @Transactional(readOnly = true)
    public List<KbArticleVersionResponse> listVersions(UUID orgId, UUID id) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));
        return kbArticleVersionRepository.findByKbArticleIdOrderByVersionDesc(article.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KbSearchResult> search(UUID orgId, String q) {
        return knowledgeBaseSearch.search(orgId, q);
    }

    @Transactional(readOnly = true)
    public List<KbSearchResult> suggest(UUID orgId, String description) {
        return knowledgeBaseSearch.suggest(orgId, description);
    }

    private void saveVersion(KbArticle article, UUID createdBy) {
        KbArticleVersion version = new KbArticleVersion();
        version.setKbArticle(article);
        version.setVersion(article.getVersion());
        version.setTitle(article.getTitle());
        version.setCategory(article.getCategory());
        version.setBody(article.getBody());
        version.setCreatedBy(createdBy);
        version.setOrgId(article.getOrgId());
        kbArticleVersionRepository.save(version);
    }

    private boolean contentChanged(KbArticle article, KbArticleUpdateRequest request) {
        String title = request.title() != null ? request.title() : article.getTitle();
        String category = request.category() != null ? request.category() : article.getCategory();
        String body = request.body() != null ? request.body() : article.getBody();
        return !title.equals(article.getTitle())
                || !category.equals(article.getCategory())
                || !body.equals(article.getBody());
    }

    private KbArticleResponse toResponse(KbArticle article, UUID userId) {
        Boolean myVote = userId == null ? null : kbFeedbackRepository
                .findByKbArticleIdAndCreatedBy(article.getId(), userId)
                .map(KbFeedback::isHelpful)
                .orElse(null);
        return new KbArticleResponse(
                article.getId(),
                article.getNumber(),
                article.getTitle(),
                article.getCategory(),
                article.getBody(),
                article.getStatus(),
                article.getAuthor().getId(),
                article.getAuthor().getDisplayName(),
                article.getViewCount(),
                article.getHelpfulCount(),
                article.getNotHelpfulCount(),
                article.getVersion(),
                article.getPublishedAt(),
                article.getArchivedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                myVote
        );
    }

    private KbArticleSummary toSummary(KbArticle article) {
        return new KbArticleSummary(
                article.getId(),
                article.getNumber(),
                article.getTitle(),
                article.getCategory(),
                article.getStatus(),
                article.getViewCount(),
                article.getHelpfulCount(),
                article.getNotHelpfulCount(),
                article.getVersion(),
                excerpt(article.getBody()),
                article.getUpdatedAt()
        );
    }

    private String excerpt(String body) {
        if (body == null) {
            return null;
        }
        String plain = body
                .replaceAll("```[\\s\\S]*?```", " ")          // fenced code blocks
                .replaceAll("`([^`]*)`", "$1")               // inline code
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")    // images
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")  // links -> text
                .replaceAll("(?m)^#{1,6}\\s*", "")           // headings
                .replaceAll("[*_~>]", "")                    // emphasis/quotes
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")        // list bullets
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= 160 ? plain : plain.substring(0, 157) + "...";
    }

    private boolean isKbAdmin(AppUser user) {
        return hasAnyRole(user, "ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean isKbContributor(AppUser user) {
        return hasAnyRole(user, "AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN",
                "ROLE_AGENT", "ROLE_TEAM_LEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean isAuthor(AppUser user, KbArticle article) {
        return article.getAuthor() != null && article.getAuthor().getId().equals(user.getId());
    }

    private boolean hasAnyRole(AppUser user, String... names) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        List<String> targets = List.of(names);
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(targets::contains);
    }

    private String generateArticleNumber() {
        Long next = ((Number) entityManager.createNativeQuery("SELECT nextval('kb_article_number_seq')")
                .getSingleResult()).longValue();
        return "KB-" + next;
    }

    private KbArticleVersionResponse toVersionResponse(KbArticleVersion version) {
        return new KbArticleVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getTitle(),
                version.getCategory(),
                version.getBody(),
                version.getCreatedAt()
        );
    }
}
