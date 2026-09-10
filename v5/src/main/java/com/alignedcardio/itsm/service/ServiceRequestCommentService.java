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

        notifyOnComment(sr, author, request);

        return toResponse(comment);
    }

    private void notifyOnComment(ServiceRequest sr, AppUser author, CommentCreateRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("title", sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "");
        payload.put("authorName", author.getDisplayName());
        payload.put("commentPreview", bodyPreview(request.body()));
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());

        NotificationContent content = null;
        try {
            content = notificationTemplateBuilder.forEvent("SR_COMMENT", payload);
        } catch (Exception e) {
            logger.warn("Could not build SR comment notification content", e);
            return;
        }

        Set<UUID> notified = new HashSet<>();
        notified.add(author.getId());

        // 1. Requester is notified only for public comments.
        if (request.isPublic() && sr.getRequester() != null && !notified.contains(sr.getRequester().getId())) {
            try {
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        sr.getRequester().getId(),
                        "SR_COMMENT",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        Notification.Channel.BOTH,
                        content));
                notified.add(sr.getRequester().getId());
            } catch (Exception e) {
                logger.warn("Failed to notify requester {} of SR comment", sr.getRequester().getId(), e);
            }
        }

        // 2. Approver (who approved/is handling the request) is always notified.
        if (sr.getApprover() != null && !notified.contains(sr.getApprover().getId())) {
            try {
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        sr.getApprover().getId(),
                        "SR_COMMENT",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        Notification.Channel.BOTH,
                        content));
                notified.add(sr.getApprover().getId());
            } catch (Exception e) {
                logger.warn("Failed to notify approver {} of SR comment", sr.getApprover().getId(), e);
            }
        }

        // 3. Currently assigned fulfiller(s) on non-completed tasks.
        try {
            List<FulfillmentTask> tasks = fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId());
            for (FulfillmentTask task : tasks) {
                if (task.getAssignee() != null
                        && task.getStatus() != FulfillmentTask.Status.COMPLETED
                        && !notified.contains(task.getAssignee().getId())) {
                    notificationService.send(new NotificationRequest(
                            sr.getOrgId(),
                            task.getAssignee().getId(),
                            "SR_COMMENT",
                            content.inAppSubject(),
                            content.inAppBody(),
                            "SERVICE_REQUEST",
                            sr.getId(),
                            Notification.Channel.BOTH,
                            content));
                    notified.add(task.getAssignee().getId());
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
                    notificationService.send(new NotificationRequest(
                            sr.getOrgId(),
                            admin.getId(),
                            "SR_COMMENT",
                            content.inAppSubject(),
                            content.inAppBody(),
                            "SERVICE_REQUEST",
                            sr.getId(),
                            Notification.Channel.BOTH,
                            content));
                    notified.add(admin.getId());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to notify admins of SR comment for {}", sr.getId(), e);
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
