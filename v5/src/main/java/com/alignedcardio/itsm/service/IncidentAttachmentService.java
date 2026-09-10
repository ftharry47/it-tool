package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.IncidentAttachmentResponse;
import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentAttachment;
import com.alignedcardio.itsm.repository.IncidentAttachmentRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentAttachmentService {

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final String storePath;

    public IncidentAttachmentService(IncidentRepository incidentRepository,
                                     IncidentAttachmentRepository attachmentRepository,
                                     @Value("${attachment.store.path:target/attachments}") String storePath) {
        this.incidentRepository = incidentRepository;
        this.attachmentRepository = attachmentRepository;
        this.storePath = storePath;
    }

    @Transactional(readOnly = true)
    public List<IncidentAttachmentResponse> listAttachments(UUID orgId, UUID incidentId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        return attachmentRepository.findByIncidentIdAndDeletedAtIsNullOrderByCreatedAtDesc(incident.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource getAttachmentResource(UUID orgId, UUID incidentId, UUID attachmentId) {
        IncidentAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        if (!attachment.getOrgId().equals(orgId) || !attachment.getIncident().getId().equals(incidentId)) {
            throw new NotFoundException("Attachment not found");
        }
        Path file = Paths.get(storePath, orgId.toString(), incidentId.toString(), attachment.getFileName());
        if (!Files.exists(file)) {
            throw new NotFoundException("Attachment file not found");
        }
        return new FileSystemResource(file);
    }

    @Transactional
    public IncidentAttachmentResponse storeAttachment(UUID orgId, UUID incidentId, AppUser user, MultipartFile file) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        Path dir = Paths.get(storePath, orgId.toString(), incident.getId().toString());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store attachment: " + e.getMessage(), e);
        }

        IncidentAttachment attachment = new IncidentAttachment();
        attachment.setOrgId(orgId);
        attachment.setIncident(incident);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setCreatedBy(user.getId());
        attachment.setUpdatedBy(user.getId());

        attachment = attachmentRepository.save(attachment);
        attachment.setBlobUrl("/api/v1/incidents/" + incident.getId() + "/attachments/" + attachment.getId());
        attachment = attachmentRepository.save(attachment);

        return toResponse(attachment);
    }

    private IncidentAttachmentResponse toResponse(IncidentAttachment attachment) {
        String blobUrl = null;
        if (attachment.getId() != null && attachment.getIncident() != null) {
            blobUrl = "/api/v1/incidents/" + attachment.getIncident().getId() + "/attachments/" + attachment.getId();
        }
        return new IncidentAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                blobUrl,
                attachment.getCreatedAt()
        );
    }
}
