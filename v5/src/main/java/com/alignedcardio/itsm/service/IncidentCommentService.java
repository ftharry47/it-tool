package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.api.incident.IncidentCommentResponse;
import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentComment;
import com.alignedcardio.itsm.event.IncidentCommentedEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.IncidentCommentRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.IncidentWatcherRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import java.util.Map;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentCommentService {

    private static final Logger logger = LoggerFactory.getLogger(IncidentCommentService.class);


    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final IncidentWatcherRepository watcherRepository;
    private final AppUserRepository appUserRepository;
    private final SlaEngine slaEngine;
    private final NotificationService notificationService;
    private final com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentCommentService(IncidentRepository incidentRepository,
                                  IncidentCommentRepository commentRepository,
                                  IncidentWatcherRepository watcherRepository,
                                  AppUserRepository appUserRepository,
                                  SlaEngine slaEngine,
                                  NotificationService notificationService,
                                  com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder,
                                  ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.watcherRepository = watcherRepository;
        this.appUserRepository = appUserRepository;
        this.slaEngine = slaEngine;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
        this.eventPublisher = eventPublisher;
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

        if (!canViewInternal(author) && !request.isPublic()) {
            throw new IllegalStateException("Only AGENT+ can post internal comments");
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

            eventPublisher.publishEvent(new IncidentCommentedEvent(
                    orgId,
                    incidentId,
                    Map.of(
                            "id", comment.getId(),
                            "incidentId", incidentId,
                            "number", incident.getNumber(),
                            "authorId", author.getId(),
                            "authorName", author.getDisplayName(),
                            "isPublic", true,
                            "requesterId", incident.getRequester().getId())));

            Map<String, Object> commentPayload = new java.util.HashMap<>();
            commentPayload.put("number", incident.getNumber());
            commentPayload.put("title", incident.getTitle());
            commentPayload.put("authorName", author.getDisplayName());
            commentPayload.put("commentPreview", bodyPreview(request.body()));
            commentPayload.put("entityType", "INCIDENT");
            commentPayload.put("entityId", incident.getId());

            Map<String, Object> newComment = new java.util.HashMap<>();
            newComment.put("authorName", author.getDisplayName());
            newComment.put("body", request.body());
            newComment.put("createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt() : OffsetDateTime.now());
            newComment.put("public", true);
            commentPayload.put("newComment", newComment);

            List<Map<String, Object>> prior = new java.util.ArrayList<>();
            for (IncidentComment c : commentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId())) {
                if (c.isPublic() && !c.getId().equals(comment.getId())) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("authorName", c.getAuthor() != null ? c.getAuthor().getDisplayName() : "");
                    m.put("body", c.getBody());
                    m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt() : OffsetDateTime.now());
                    m.put("public", c.isPublic());
                    prior.add(m);
                }
            }
            commentPayload.put("priorComments", prior);
            notifyWatchers(incident, author, "INCIDENT_COMMENT", commentPayload);
        }
        // Mentions fire on internal notes too — but only for staff who can
        // actually see them.
        notifyMentions(incident, author, orgId, request.body(), request.isPublic());

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

    private String bodyPreview(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "there";
        return displayName.trim().split("\\s+")[0];
    }

    private void notifyWatchers(Incident incident, AppUser author, String type, Map<String, ?> payload) {
        watcherRepository.findByIncidentIdAndDeletedAtIsNull(incident.getId()).forEach(w -> {
            if (w.getUser().getId().equals(author.getId())) return;
            try {
                Map<String, Object> userPayload = new java.util.HashMap<>(payload);
                userPayload.put("recipientFirstName", firstName(w.getUser().getDisplayName()));
                var content = notificationTemplateBuilder.forEvent(type, userPayload);
                notificationService.send(new NotificationRequest(
                        incident.getOrgId(),
                        w.getUser().getId(),
                        type,
                        content.inAppSubject(),
                        content.inAppBody(),
                        "INCIDENT",
                        incident.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to notify watcher {}", w.getUser().getId(), e);
            }
        });
    }

    /**
     * Mentions resolve against real org users — "@Display Name" (spaces OK,
     * inserted by the composer picker) or "@email". Internal comments only
     * notify staff, since the mention must be visible to the recipient.
     */
    private void notifyMentions(Incident incident, AppUser author, UUID orgId, String body, boolean isPublic) {
        if (body == null || body.isBlank()) return;
        for (AppUser u : appUserRepository.findByOrgId(orgId)) {
            if (u.getId().equals(author.getId()) || u.getDeletedAt() != null) continue;
            if (!isPublic && !canViewInternal(u)) continue;
            if (containsMention(body, u.getEmail()) || containsMention(body, u.getDisplayName())) {
                sendMention(incident, author, u, body);
            }
        }
    }

    private boolean containsMention(String body, String token) {
        return token != null && !token.isBlank()
                && Pattern.compile("@" + Pattern.quote(token), Pattern.CASE_INSENSITIVE)
                        .matcher(body).find();
    }

    private void sendMention(Incident incident, AppUser author, AppUser mentioned, String commentBody) {
        if (mentioned.getId().equals(author.getId())) return;
        try {
            Map<String, Object> mentionPayload = new java.util.HashMap<>();
            mentionPayload.put("number", incident.getNumber());
            mentionPayload.put("title", incident.getTitle());
            mentionPayload.put("authorName", author.getDisplayName());
            mentionPayload.put("commentPreview", bodyPreview(commentBody));
            mentionPayload.put("recipientFirstName", firstName(mentioned.getDisplayName()));
            mentionPayload.put("entityType", "INCIDENT");
            mentionPayload.put("entityId", incident.getId());
            mentionPayload.put("entityPath", "/dashboard/incidents/" + incident.getId());
            var content = notificationTemplateBuilder.forEvent("MENTION", mentionPayload);
            notificationService.send(new NotificationRequest(
                    incident.getOrgId(),
                    mentioned.getId(),
                    "MENTION",
                    content.inAppSubject(),
                    content.inAppBody(),
                    "INCIDENT",
                    incident.getId(),
                    null,
                    content));
        } catch (Exception e) {
            logger.warn("Failed to notify mentioned user {}", mentioned.getId(), e);
        }
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
