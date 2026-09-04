package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbSearchResult;
import com.alignedcardio.itsm.entity.KbArticle;
import com.alignedcardio.itsm.repository.KbArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IlikeKnowledgeBaseSearchTest {

    @Mock
    private KbArticleRepository kbArticleRepository;

    @InjectMocks
    private IlikeKnowledgeBaseSearch search;

    @Test
    void searchReturnsArticleForKeyword() {
        UUID orgId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        KbArticle article = new KbArticle();
        article.setId(articleId);
        article.setNumber("42");
        article.setTitle("Reset password");
        article.setCategory("Password");

        when(kbArticleRepository.searchByIlike(eq(orgId), anyString()))
                .thenReturn(List.of(article));

        List<KbSearchResult> results = search.search(orgId, "password");

        assertEquals(1, results.size());
        assertEquals(articleId, results.get(0).id());
        assertEquals("Reset password", results.get(0).title());
    }

    @Test
    void suggestReturnsArticleForDescription() {
        UUID orgId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        KbArticle article = new KbArticle();
        article.setId(articleId);
        article.setNumber("7");
        article.setTitle("VPN setup");
        article.setCategory("Network");

        when(kbArticleRepository.searchByIlike(eq(orgId), anyString()))
                .thenReturn(List.of(article));

        List<KbSearchResult> results = search.suggest(orgId, "Cannot connect to VPN");

        assertEquals(1, results.size());
        assertEquals(articleId, results.get(0).id());
        assertEquals("VPN setup", results.get(0).title());
    }
}
