package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.KbArticleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbArticleVersionRepository extends JpaRepository<KbArticleVersion, UUID> {

    List<KbArticleVersion> findByKbArticleIdOrderByVersionDesc(UUID kbArticleId);

    Optional<KbArticleVersion> findByKbArticleIdAndVersion(UUID kbArticleId, int version);
}
