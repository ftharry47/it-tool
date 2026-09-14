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

    List<SlaInstance> findByIncidentIdIn(java.util.Collection<UUID> incidentIds);

    List<SlaInstance> findByProblem_IdIn(java.util.Collection<UUID> problemIds);

    List<SlaInstance> findByChangeRequest_IdIn(java.util.Collection<UUID> changeRequestIds);

    List<SlaInstance> findByPolicy_IdAndResolutionMetAtIsNull(UUID policyId);

    Optional<SlaInstance> findByIncidentIdAndOrgId(UUID incidentId, UUID orgId);

    Optional<SlaInstance> findByServiceRequest_Id(UUID serviceRequestId);

    Optional<SlaInstance> findByProblem_Id(UUID problemId);

    Optional<SlaInstance> findByChangeRequest_Id(UUID changeRequestId);

    List<SlaInstance> findByBreachStatusIn(List<SlaInstance.BreachStatus> statuses);

    List<SlaInstance> findByIncidentIsNotNullAndBreachStatusIn(List<SlaInstance.BreachStatus> statuses);
}
