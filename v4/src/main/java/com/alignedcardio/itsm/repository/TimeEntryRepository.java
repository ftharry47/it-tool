package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    List<TimeEntry> findByEntityTypeAndEntityIdAndDeletedAtIsNull(String entityType, UUID entityId);
    List<TimeEntry> findByOrgIdAndEntityTypeAndEntityIdAndDeletedAtIsNull(UUID orgId, String entityType, UUID entityId);
}
