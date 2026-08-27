package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbSearchResult;
import com.alignedcardio.itsm.entity.KbArticle;
import com.alignedcardio.itsm.repository.KbArticleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// H2 / test-only fallback. Real production searches always use the tsvector implementation.
@Component
@Profile("test")
public class IlikeKnowledgeBaseSearch implements KnowledgeBaseSearch {

    private final KbArticleRepository kbArticleRepository;

    public IlikeKnowledgeBaseSearch(KbArticleRepository kbArticleRepository) {
        this.kbArticleRepository = kbArticleRepository;
    }

    @Override
    public List<KbSearchResult> search(UUID orgId, String q) {
        return kbArticleRepository.searchByIlike(orgId, q).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    public List<KbSearchResult> suggest(UUID orgId, String description) {
        return kbArticleRepository.searchByIlike(orgId, description).stream()
                .limit(5)
                .map(this::toResult)
                .toList();
    }

    private KbSearchResult toResult(KbArticle a) {
        return new KbSearchResult(a.getId(), a.getNumber(), a.getTitle(), a.getCategory(), 0.0);
    }
}
