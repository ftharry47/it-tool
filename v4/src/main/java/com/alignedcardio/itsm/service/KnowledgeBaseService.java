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

        return toResponse(saved);
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

    @Transactional(readOnly = true)
    public KbArticleResponse get(UUID orgId, UUID id) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));
        article.setViewCount(article.getViewCount() + 1);
        kbArticleRepository.save(article);
        return toResponse(article);
    }

    @Transactional
    public KbArticleResponse update(AppUser user, UUID orgId, UUID id, KbArticleUpdateRequest request) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));

        KbArticle.Status newStatus = request.status() != null ? request.status() : article.getStatus();
        KbStatusMachine.validate(article.getStatus(), newStatus);

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

        return toResponse(kbArticleRepository.save(article));
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));
        article.setDeletedAt(OffsetDateTime.now());
        kbArticleRepository.save(article);
    }

    @Transactional
    public KbArticleResponse addFeedback(AppUser user, UUID orgId, UUID id, KbFeedbackRequest request) {
        KbArticle article = kbArticleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Knowledge base article not found"));

        KbFeedback feedback = new KbFeedback();
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

        return toResponse(kbArticleRepository.save(article));
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

    private KbArticleResponse toResponse(KbArticle article) {
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
                article.getCreatedAt()
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
                article.getVersion()
        );
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
