package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IncidentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentLinkRepository extends JpaRepository<IncidentLink, UUID> {

    List<IncidentLink> findByFromIncidentIdAndDeletedAtIsNull(UUID fromIncidentId);

    List<IncidentLink> findByToIncidentIdAndDeletedAtIsNull(UUID toIncidentId);
}
