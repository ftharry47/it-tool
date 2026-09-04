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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Map;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentCommentService {

    private static final Logger logger = LoggerFactory.getLogger(IncidentCommentService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.\\-@]+)");

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final IncidentWatcherRepository watcherRepository;
    private final AppUserRepository appUserRepository;
    private final SlaEngine slaEngine;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentCommentService(IncidentRepository incidentRepository,
                                  IncidentCommentRepository commentRepository,
                                  IncidentWatcherRepository watcherRepository,
                                  AppUserRepository appUserRepository,
                                  SlaEngine slaEngine,
                                  NotificationService notificationService,
                                  ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.watcherRepository = watcherRepository;
        this.appUserRepository = appUserRepository;
        this.slaEngine = slaEngine;
        this.notificationService = notificationService;
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

            String subject = "New comment on Incident " + incident.getNumber() + " by " + author.getDisplayName();
            String body = "Comment: " + bodyPreview(request.body());
            notifyWatchers(incident, author, subject, body);
            notifyMentions(incident, author, orgId, request.body());
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

    private String bodyPreview(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private void notifyWatchers(Incident incident, AppUser author, String subject, String body) {
        watcherRepository.findByIncidentIdAndDeletedAtIsNull(incident.getId()).forEach(w -> {
            if (w.getUser().getId().equals(author.getId())) return;
            try {
                notificationService.send(new NotificationRequest(
                        incident.getOrgId(),
                        w.getUser().getId(),
                        "INCIDENT_COMMENT",
                        subject,
                        body,
                        "INCIDENT",
                        incident.getId(),
                        null));
            } catch (Exception e) {
                logger.warn("Failed to notify watcher {}", w.getUser().getId(), e);
            }
        });
    }

    private void notifyMentions(Incident incident, AppUser author, UUID orgId, String body) {
        if (body == null || body.isBlank()) return;
        Set<String> seen = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(body);
        while (matcher.find()) {
            String mention = matcher.group(1);
            if (!seen.add(mention)) continue;

            appUserRepository.findByOrgIdAndEmailIgnoreCase(orgId, mention)
                    .ifPresent(mentioned -> sendMention(incident, author, mentioned));
        }
    }

    private void sendMention(Incident incident, AppUser author, AppUser mentioned) {
        if (mentioned.getId().equals(author.getId())) return;
        try {
            notificationService.send(new NotificationRequest(
                    incident.getOrgId(),
                    mentioned.getId(),
                    "MENTION",
                    "You were mentioned in Incident " + incident.getNumber(),
                    author.getDisplayName() + " mentioned you in a comment.",
                    "INCIDENT",
                    incident.getId(),
                    null));
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
