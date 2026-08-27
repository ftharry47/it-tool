package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SavedReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedReportRepository extends JpaRepository<SavedReport, UUID> {

    List<SavedReport> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<SavedReport> findByIdAndOrgId(UUID id, UUID orgId);
}
