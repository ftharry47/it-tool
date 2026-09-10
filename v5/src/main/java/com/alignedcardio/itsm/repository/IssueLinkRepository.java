package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IssueLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {

    List<IssueLink> findByFromIssueId(UUID fromIssueId);

    List<IssueLink> findByToIssueId(UUID toIssueId);
}
