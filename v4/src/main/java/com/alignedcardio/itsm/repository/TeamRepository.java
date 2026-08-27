package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByOrgIdAndDeletedAtIsNullOrderByName(UUID orgId);

    Optional<Team> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);
}
