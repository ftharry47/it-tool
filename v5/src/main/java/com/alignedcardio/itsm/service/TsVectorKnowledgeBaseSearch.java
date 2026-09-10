package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Production PostgreSQL tsvector search path. Uses to_tsvector / plainto_tsquery.
@Component
@Profile("!test")
public class TsVectorKnowledgeBaseSearch implements KnowledgeBaseSearch {

    private final EntityManager entityManager;

    public TsVectorKnowledgeBaseSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<KbSearchResult> search(UUID orgId, String q) {
        return runQuery(orgId, q, 20);
    }

    @Override
    public List<KbSearchResult> suggest(UUID orgId, String description) {
        return runQuery(orgId, description, 5);
    }

    @SuppressWarnings("unchecked")
    private List<KbSearchResult> runQuery(UUID orgId, String q, int max) {
        String sql = """
                SELECT a.id, a.number, a.title, a.category,
                       ts_rank(to_tsvector('english', coalesce(a.title,'') || ' ' || coalesce(a.body,'')),
                               plainto_tsquery('english', :q)) as rank
                FROM kb_article a
                WHERE a.org_id = :orgId
                  AND a.status = 'PUBLISHED'
                  AND to_tsvector('english', coalesce(a.title,'') || ' ' || coalesce(a.body,''))
                      @@ plainto_tsquery('english', :q)
                ORDER BY rank DESC
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("orgId", orgId);
        query.setParameter("q", q);
        query.setMaxResults(max);

        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toResult).collect(Collectors.toList());
    }

    private KbSearchResult toResult(Object[] row) {
        return new KbSearchResult(
                (UUID) row[0],
                row[1] != null ? String.valueOf(row[1]) : null,
                (String) row[2],
                (String) row[3],
                row[4] != null ? ((Number) row[4]).doubleValue() : 0.0
        );
    }
}
