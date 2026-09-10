package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IncidentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, UUID> {

    List<IncidentAttachment> findByIncidentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID incidentId);
}
