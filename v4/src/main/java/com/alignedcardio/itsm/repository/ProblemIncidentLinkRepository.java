package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ProblemIncidentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProblemIncidentLinkRepository extends JpaRepository<ProblemIncidentLink, ProblemIncidentLink.ProblemIncidentLinkId> {

    List<ProblemIncidentLink> findByProblemId(UUID problemId);

    List<ProblemIncidentLink> findByIncidentId(UUID incidentId);

    boolean existsByProblemIdAndIncidentId(UUID problemId, UUID incidentId);
}
