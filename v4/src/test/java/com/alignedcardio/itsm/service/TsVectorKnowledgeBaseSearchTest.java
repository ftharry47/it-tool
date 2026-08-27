package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TsVectorKnowledgeBaseSearchTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Test
    void searchUsesTsVectorAndReturnsExpectedArticle() {
        UUID articleId = UUID.randomUUID();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        Object[] row = new Object[]{ articleId, 42L, "Reset password", "Password", 0.75 };
        List<Object> rows = new ArrayList<>();
        rows.add(row);
        when(query.getResultList()).thenReturn(rows);

        TsVectorKnowledgeBaseSearch search = new TsVectorKnowledgeBaseSearch(entityManager);
        List<KbSearchResult> results = search.search(UUID.randomUUID(), "password");

        assertEquals(1, results.size());
        assertEquals(articleId, results.get(0).id());
        assertEquals("Reset password", results.get(0).title());
        assertEquals("Password", results.get(0).category());

        verify(entityManager).createNativeQuery(argThat(sql -> sql.contains("to_tsvector") && sql.contains("plainto_tsquery")));
    }

    @Test
    void suggestUsesTsVectorAndReturnsExpectedArticle() {
        UUID articleId = UUID.randomUUID();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        Object[] row = new Object[]{ articleId, 7L, "VPN setup", "Network", 0.5 };
        List<Object> rows = new ArrayList<>();
        rows.add(row);
        when(query.getResultList()).thenReturn(rows);

        TsVectorKnowledgeBaseSearch search = new TsVectorKnowledgeBaseSearch(entityManager);
        List<KbSearchResult> results = search.suggest(UUID.randomUUID(), "Cannot connect to VPN");

        assertEquals(1, results.size());
        assertEquals(articleId, results.get(0).id());
        assertEquals("VPN setup", results.get(0).title());

        verify(query).setMaxResults(5);
    }
}
