package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByOrgIdAndDeletedAtIsNullOrderByName(UUID orgId);

    Optional<Location> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);

    Optional<Location> findByOrgIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID orgId, String name);

    boolean existsByApprovalManager_IdAndDeletedAtIsNull(UUID userId);
}
