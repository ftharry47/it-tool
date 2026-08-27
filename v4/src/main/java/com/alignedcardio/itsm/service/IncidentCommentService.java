package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.api.incident.IncidentCommentResponse;
import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentComment;
import com.alignedcardio.itsm.repository.IncidentCommentRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentCommentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final SlaEngine slaEngine;

    public IncidentCommentService(IncidentRepository incidentRepository,
                                  IncidentCommentRepository commentRepository,
                                  SlaEngine slaEngine) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.slaEngine = slaEngine;
    }

    @Transactional(readOnly = true)
    public List<IncidentCommentResponse> listComments(UUID orgId, UUID incidentId, AppUser viewer) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        boolean canViewInternal = canViewInternal(viewer);

        return commentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
                .filter(c -> canViewInternal || c.isPublic())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public IncidentCommentResponse addComment(UUID orgId, UUID incidentId, AppUser author, CommentCreateRequest request) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        if (!canCommentAsPublic(author) && request.isPublic()) {
            throw new IllegalStateException("Only AGENT+ can post public comments");
        }

        IncidentComment comment = new IncidentComment();
        comment.setOrgId(orgId);
        comment.setIncident(incident);
        comment.setAuthor(author);
        comment.setBody(request.body());
        comment.setPublic(request.isPublic());
        comment.setAuthorType(CommentAuthorType.USER);
        comment.setCreatedBy(author.getId());
        comment.setUpdatedBy(author.getId());

        comment = commentRepository.save(comment);

        if (request.isPublic()) {
            OffsetDateTime respondedAt = Optional.ofNullable(comment.getCreatedAt()).orElse(OffsetDateTime.now());
            slaEngine.recordFirstResponse(incident, respondedAt);
        }

        return toResponse(comment);
    }

    @Transactional
    public IncidentCommentResponse addAutomationComment(UUID orgId, UUID incidentId, AppUser author, String body) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        IncidentComment comment = new IncidentComment();
        comment.setOrgId(orgId);
        comment.setIncident(incident);
        comment.setAuthor(author);
        comment.setBody(body);
        comment.setPublic(false);
        comment.setAuthorType(CommentAuthorType.AUTOMATION);
        comment.setCreatedBy(author.getId());
        comment.setUpdatedBy(author.getId());

        comment = commentRepository.save(comment);

        return toResponse(comment);
    }

    private boolean canViewInternal(AppUser user) {
        return user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .anyMatch(r -> List.of("AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN").contains(r));
    }

    private boolean canCommentAsPublic(AppUser user) {
        return canViewInternal(user);
    }

    private IncidentCommentResponse toResponse(IncidentComment comment) {
        return new IncidentCommentResponse(
                comment.getId(),
                comment.getBody(),
                comment.isPublic(),
                Optional.ofNullable(comment.getAuthor()).map(AppUser::getDisplayName).orElse(null),
                comment.getAuthorType().name(),
                comment.getCreatedAt()
        );
    }
}
