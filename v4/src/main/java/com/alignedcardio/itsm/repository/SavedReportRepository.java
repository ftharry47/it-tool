package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SavedReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedReportRepository extends JpaRepository<SavedReport, UUID> {

    List<SavedReport> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<SavedReport> findByIdAndOrgId(UUID id, UUID orgId);

    /** Ad-hoc org reports + generated reports owned by the given user. */
    List<SavedReport> findByOrgIdAndOwnerUserIdIsNullOrOwnerUserIdOrderByCreatedAtDesc(UUID orgId, UUID ownerUserId);

    /** All generated performance reports for a period (SUPER_ADMIN aggregate view). */
    List<SavedReport> findByOrgIdAndReportTypeOrderByCreatedAtDesc(UUID orgId, String reportType);
}
