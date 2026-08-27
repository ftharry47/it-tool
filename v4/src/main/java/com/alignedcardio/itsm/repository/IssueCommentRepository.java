package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

    List<IssueComment> findByIssueIdOrderByCreatedAtDesc(UUID issueId);
}
