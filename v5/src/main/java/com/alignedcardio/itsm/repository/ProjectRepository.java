package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID orgId);

    Optional<Project> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);

    Optional<Project> findByOrgIdAndKeyAndDeletedAtIsNull(UUID orgId, String key);
}
