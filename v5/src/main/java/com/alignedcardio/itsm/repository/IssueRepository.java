package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    List<Issue> findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

    List<Issue> findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

    List<Issue> findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(UUID sprintId);

    Optional<Issue> findByProjectIdAndIdAndDeletedAtIsNull(UUID projectId, UUID id);

    long countBySprintIdAndDeletedAtIsNull(UUID sprintId);
}
