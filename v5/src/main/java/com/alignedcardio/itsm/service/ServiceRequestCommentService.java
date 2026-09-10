package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestCommentResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.ServiceRequestComment;
import com.alignedcardio.itsm.repository.ServiceRequestCommentRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceRequestCommentService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRequestCommentService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final NotificationTemplateBuilder notificationTemplateBuilder;

    public ServiceRequestCommentService(ServiceRequestRepository serviceRequestRepository,
                                        ServiceRequestCommentRepository commentRepository,
                                        NotificationService notificationService,
                                        NotificationTemplateBuilder notificationTemplateBuilder) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.commentRepository = commentRepository;
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

        // Public comments notify the requester (unless they wrote it themselves).
        if (request.isPublic() && sr.getRequester() != null
                && !sr.getRequester().getId().equals(author.getId())) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("number", sr.getNumber());
                payload.put("title", sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "");
                payload.put("authorName", author.getDisplayName());
                payload.put("commentPreview", bodyPreview(request.body()));
                payload.put("entityType", "SERVICE_REQUEST");
                payload.put("entityId", sr.getId());
                var content = notificationTemplateBuilder.forEvent("SR_COMMENT", payload);
                notificationService.send(new NotificationRequest(
                        orgId,
                        sr.getRequester().getId(),
                        "SR_COMMENT",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        Notification.Channel.BOTH,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to notify requester {} of SR comment", sr.getRequester().getId(), e);
            }
        }

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
