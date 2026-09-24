package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestCommentResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.ServiceRequestComment;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.ServiceRequestCommentRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.alignedcardio.itsm.service.notification.NotificationContent;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceRequestCommentService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRequestCommentService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestCommentRepository commentRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;
    private final NotificationTemplateBuilder notificationTemplateBuilder;

    public ServiceRequestCommentService(ServiceRequestRepository serviceRequestRepository,
                                        ServiceRequestCommentRepository commentRepository,
                                        FulfillmentTaskRepository fulfillmentTaskRepository,
                                        AppUserRepository appUserRepository,
                                        NotificationService notificationService,
                                        NotificationTemplateBuilder notificationTemplateBuilder) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.commentRepository = commentRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.appUserRepository = appUserRepository;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestCommentResponse> listComments(UUID orgId, UUID requestId, AppUser viewer) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        boolean canViewInternal = canViewInternal(viewer);

        return commentRepository.findByServiceRequestIdOrderByCreatedAtAsc(sr.getId()).stream()
                .filter(c -> canViewInternal || c.isPublic())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceRequestCommentResponse addComment(UUID orgId, UUID requestId, AppUser author, CommentCreateRequest request) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        if (!canViewInternal(author) && !request.isPublic()) {
            throw new IllegalStateException("Only AGENT+ can post internal comments");
        }

        ServiceRequestComment comment = new ServiceRequestComment();
        comment.setOrgId(orgId);
        comment.setServiceRequest(sr);
        comment.setAuthor(author);
        comment.setBody(request.body());
        comment.setPublic(request.isPublic());
        comment.setAuthorType(CommentAuthorType.USER);
        comment.setCreatedBy(author.getId());
        comment.setUpdatedBy(author.getId());

        comment = commentRepository.save(comment);

        notifyOnComment(sr, author, comment, request);
        // Mentions fire on internal notes too — but only for staff who can
        // actually see them.
        notifyMentions(sr, author, request.body(), request.isPublic());

        return toResponse(comment);
    }

    /**
     * Mentions resolve against real org users — "@Display Name" (spaces OK,
     * inserted by the composer picker) or "@email". Internal comments only
     * notify staff, since the mention must be visible to the recipient.
     */
    private void notifyMentions(ServiceRequest sr, AppUser author, String body, boolean isPublic) {
        if (body == null || body.isBlank()) return;
        for (AppUser u : appUserRepository.findByOrgId(sr.getOrgId())) {
            if (u.getId().equals(author.getId()) || u.getDeletedAt() != null) continue;
            if (!isPublic && !canViewInternal(u)) continue;
            if (containsMention(body, u.getEmail()) || containsMention(body, u.getDisplayName())) {
                try {
                    Map<String, Object> mentionPayload = new HashMap<>();
                    mentionPayload.put("number", sr.getNumber());
                    mentionPayload.put("title", sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "");
                    mentionPayload.put("authorName", author.getDisplayName());
                    mentionPayload.put("commentPreview", bodyPreview(body));
                    mentionPayload.put("recipientFirstName", firstName(u.getDisplayName()));
                    mentionPayload.put("entityType", "SERVICE_REQUEST");
                    mentionPayload.put("entityId", sr.getId());
                    mentionPayload.put("entityPath", "/dashboard/service-requests/" + sr.getId());
                    var content = notificationTemplateBuilder.forEvent("MENTION", mentionPayload);
                    notificationService.send(new NotificationRequest(
                            sr.getOrgId(), u.getId(), "MENTION",
                            content.inAppSubject(), content.inAppBody(),
                            "SERVICE_REQUEST", sr.getId(), null, content));
                } catch (Exception e) {
                    logger.warn("Failed to notify mentioned user {}", u.getId(), e);
                }
            }
        }
    }

    private boolean containsMention(String body, String token) {
        return token != null && !token.isBlank()
                && Pattern.compile("@" + Pattern.quote(token), Pattern.CASE_INSENSITIVE)
                        .matcher(body).find();
    }

    private String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "there";
        return displayName.trim().split("\\s+")[0];
    }

    private void notifyOnComment(ServiceRequest sr, AppUser author, ServiceRequestComment comment, CommentCreateRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("title", sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "");
        payload.put("authorName", author.getDisplayName());
        payload.put("commentPreview", bodyPreview(request.body()));
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());

        Map<String, Object> newComment = new HashMap<>();
        newComment.put("authorName", author.getDisplayName());
        newComment.put("body", request.body());
        newComment.put("createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt() : OffsetDateTime.now());
        newComment.put("public", request.isPublic());
        payload.put("newComment", newComment);

        List<Map<String, Object>> prior = new ArrayList<>();
        for (ServiceRequestComment c : commentRepository.findByServiceRequestIdOrderByCreatedAtAsc(sr.getId())) {
            if (c.isPublic() && !c.getId().equals(comment.getId())) {
                Map<String, Object> m = new HashMap<>();
                m.put("authorName", c.getAuthor() != null ? c.getAuthor().getDisplayName() : "");
                m.put("body", c.getBody());
                m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt() : OffsetDateTime.now());
                m.put("public", c.isPublic());
                prior.add(m);
            }
        }
        payload.put("priorComments", prior);

        Set<UUID> notified = new HashSet<>();
        notified.add(author.getId());

        // 1. Requester is notified only for public comments.
        if (request.isPublic() && sr.getRequester() != null && !notified.contains(sr.getRequester().getId())) {
            sendTo(sr, sr.getRequester().getId(), "SR_COMMENT", payload, notified);
        }

        // 2. Approver (who approved/is handling the request) is always notified.
        if (sr.getApprover() != null && !notified.contains(sr.getApprover().getId())) {
            sendTo(sr, sr.getApprover().getId(), "SR_COMMENT", payload, notified);
        }

        // 3. Currently assigned fulfiller(s) on non-completed tasks.
        try {
            List<FulfillmentTask> tasks = fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId());
            for (FulfillmentTask task : tasks) {
                if (task.getAssignee() != null
                        && task.getStatus() != FulfillmentTask.Status.COMPLETED
                        && !notified.contains(task.getAssignee().getId())) {
                    sendTo(sr, task.getAssignee().getId(), "SR_COMMENT", payload, notified);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to notify fulfiller(s) of SR comment for {}", sr.getId(), e);
        }

        // 4. ADMIN / SUPER_ADMIN are always notified.
        try {
            List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(sr.getOrgId(), List.of("ADMIN", "SUPER_ADMIN"));
            for (AppUser admin : admins) {
                if (!notified.contains(admin.getId())) {
                    sendTo(sr, admin.getId(), "SR_COMMENT", payload, notified);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to notify admins of SR comment for {}", sr.getId(), e);
        }
    }

    private void sendTo(ServiceRequest sr, UUID userId, String type, Map<String, Object> payload, Set<UUID> notified) {
        try {
            Map<String, Object> userPayload = new HashMap<>(payload);
            AppUser user = appUserRepository.findById(userId).orElse(null);
            userPayload.put("recipientFirstName", firstName(user != null ? user.getDisplayName() : null));
            var content = notificationTemplateBuilder.forEvent(type, userPayload);
            notificationService.send(new NotificationRequest(
                    sr.getOrgId(),
                    userId,
                    type,
                    content.inAppSubject(),
                    content.inAppBody(),
                    "SERVICE_REQUEST",
                    sr.getId(),
                    Notification.Channel.BOTH,
                    content));
            notified.add(userId);
        } catch (Exception e) {
            logger.warn("Failed to send {} notification to {}", type, userId, e);
        }
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

    private ServiceRequestCommentResponse toResponse(ServiceRequestComment comment) {
        return new ServiceRequestCommentResponse(
                comment.getId(),
                comment.getBody(),
                comment.isPublic(),
                Optional.ofNullable(comment.getAuthor()).map(AppUser::getDisplayName).orElse(null),
                comment.getAuthorType().name(),
                comment.getCreatedAt()
        );
    }
}
