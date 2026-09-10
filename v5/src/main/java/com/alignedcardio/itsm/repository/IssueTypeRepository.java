package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueTypeRepository extends JpaRepository<IssueType, UUID> {

    List<IssueType> findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID orgId);

    Optional<IssueType> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);
}
