package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.KbArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbArticleRepository extends JpaRepository<KbArticle, UUID> {

    List<KbArticle> findByOrgIdAndStatusOrderByUpdatedAtDesc(UUID orgId, KbArticle.Status status);

    Optional<KbArticle> findByOrgIdAndId(UUID orgId, UUID id);

    @Query("SELECT k FROM KbArticle k WHERE k.orgId = :orgId AND k.status = 'PUBLISHED' AND " +
            "(LOWER(k.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(k.body) LIKE LOWER(CONCAT('%', :q, '%')))" +
            "ORDER BY k.updatedAt DESC")
    List<KbArticle> searchByIlike(@Param("orgId") UUID orgId, @Param("q") String q);
}
