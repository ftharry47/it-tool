package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SlaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlaInstanceRepository extends JpaRepository<SlaInstance, UUID> {

    Optional<SlaInstance> findByIncidentId(UUID incidentId);

    Optional<SlaInstance> findByIncidentIdAndOrgId(UUID incidentId, UUID orgId);

    List<SlaInstance> findByBreachStatusIn(List<SlaInstance.BreachStatus> statuses);

    List<SlaInstance> findByIncidentIsNotNullAndBreachStatusIn(List<SlaInstance.BreachStatus> statuses);
}
