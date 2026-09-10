package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IncidentWatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentWatcherRepository extends JpaRepository<IncidentWatcher, UUID> {

    List<IncidentWatcher> findByIncidentIdAndDeletedAtIsNull(UUID incidentId);

    Optional<IncidentWatcher> findByIncidentIdAndUserId(UUID incidentId, UUID userId);
}
