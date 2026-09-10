package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.KbFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbFeedbackRepository extends JpaRepository<KbFeedback, UUID> {

    List<KbFeedback> findByKbArticleIdOrderByCreatedAtDesc(UUID kbArticleId);

    Optional<KbFeedback> findByKbArticleIdAndCreatedBy(UUID kbArticleId, UUID createdBy);
}
