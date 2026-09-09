package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.change.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.ChangeApproval;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.ChangeApprovalRepository;
import com.alignedcardio.itsm.repository.ChangeRequestRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChangeService {

    private static final Logger logger = LoggerFactory.getLogger(ChangeService.class);

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeApprovalRepository changeApprovalRepository;
    private final AppUserRepository appUserRepository;
    private final ProblemRepository problemRepository;
    private final LocationRepository locationRepository;
    private final EntityManager entityManager;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ChangeService(ChangeRequestRepository changeRequestRepository,
                         ChangeApprovalRepository changeApprovalRepository,
                         AppUserRepository appUserRepository,
                         ProblemRepository problemRepository,
                         LocationRepository locationRepository,
                         EntityManager entityManager,
                         AuditLogRepository auditLogRepository,
                         ObjectMapper objectMapper) {
        this.changeRequestRepository = changeRequestRepository;
        this.changeApprovalRepository = changeApprovalRepository;
        this.appUserRepository = appUserRepository;
        this.problemRepository = problemRepository;
        this.locationRepository = locationRepository;
        this.entityManager = entityManager;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ChangeResponse> list(UUID orgId) {
        return changeRequestRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeResponse> search(UUID orgId, String query, int limit) {
        return changeRequestRepository.searchByText(orgId, query, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChangeResponse create(AppUser user, UUID orgId, ChangeCreateRequest request) {
        if (!isChangeContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can create change requests");
        }

        ChangeRequest change = new ChangeRequest();
        change.setOrgId(orgId);
        change.setTitle(request.title());
        change.setDescription(request.description());
        change.setChangeType(request.changeType());
        change.setRisk(request.risk());
        change.setPlannedStart(request.plannedStart());
        change.setPlannedEnd(request.plannedEnd());
        change.setRollbackPlan(request.rollbackPlan());
        change.setNumber(generateChangeNumber());
        change.setCreatedBy(user.getId());
        change.setUpdatedBy(user.getId());

        if (request.requestedById() != null) {
            AppUser req = appUserRepository.findById(request.requestedById())
                    .orElseThrow(() -> new NotFoundException("Requester not found"));
            change.setRequestedBy(req);
        }

        if (request.linkedProblemId() != null) {
            Problem problem = problemRepository.findByOrgIdAndId(orgId, request.linkedProblemId())
                    .orElseThrow(() -> new NotFoundException("Problem not found"));
            change.setLinkedProblem(problem);
        }

        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            change.setLocation(location);
        }

        ChangeRequest saved = changeRequestRepository.save(change);
        entityManager.flush();
        entityManager.refresh(saved);
        writeChangeAudit(saved, user.getId(), "CREATE", null, changeAuditState(saved));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ChangeResponse get(UUID orgId, UUID id) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        return toResponse(change);
    }

    @Transactional
    public ChangeResponse update(AppUser user, UUID orgId, UUID id, ChangeUpdateRequest request) {
        if (!isChangeContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can update change requests");
        }

        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        Map<String, Object> beforeState = changeAuditState(change);

        if (request.title() != null) change.setTitle(request.title());
        if (request.description() != null) change.setDescription(request.description());
        if (request.changeType() != null) change.setChangeType(request.changeType());
        if (request.risk() != null) change.setRisk(request.risk());
        if (request.plannedStart() != null) change.setPlannedStart(request.plannedStart());
        if (request.plannedEnd() != null) change.setPlannedEnd(request.plannedEnd());
        if (request.rollbackPlan() != null) change.setRollbackPlan(request.rollbackPlan());
        if (request.postImplementationReview() != null) change.setPostImplementationReview(request.postImplementationReview());

        if (request.requestedById() != null) {
            AppUser req = appUserRepository.findById(request.requestedById())
                    .orElseThrow(() -> new NotFoundException("Requester not found"));
            change.setRequestedBy(req);
        }

        if (request.linkedProblemId() != null) {
            Problem problem = problemRepository.findByOrgIdAndId(orgId, request.linkedProblemId())
                    .orElseThrow(() -> new NotFoundException("Problem not found"));
            change.setLinkedProblem(problem);
        }

        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            change.setLocation(location);
        }

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        writeChangeFieldUpdateAudit(saved, user.getId(), beforeState, false);

        return toResponse(saved);
    }

    @Transactional
    public ChangeResponse updateStatus(AppUser user, UUID orgId, UUID id, ChangeRequest.Status newStatus) {
        if (!isChangeContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can update change status");
        }

        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        ChangeRequest.Status oldStatus = change.getStatus();

        ChangeStatusMachine.validate(change, newStatus);
        validateChangeTransition(user, change, newStatus);
        change.setStatus(newStatus);
        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        writeChangeAudit(saved, user.getId(), "STATUS",
                Map.of("status", oldStatus.name()),
                Map.of("status", saved.getStatus().name()));

        return toResponse(saved);
    }

    @Transactional
    public ChangeResponse submitForApproval(AppUser user, UUID orgId, UUID id) {
        if (!isChangeContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can submit change requests");
        }

        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        ChangeRequest.Status oldStatus = change.getStatus();

        if (change.getStatus() != ChangeRequest.Status.DRAFT) {
            throw new IllegalStateException("Only DRAFT change requests can be submitted for approval");
        }

        if (change.getChangeType() == ChangeRequest.ChangeType.STANDARD) {
            change.setStatus(ChangeRequest.Status.APPROVED);
        } else {
            change.setStatus(ChangeRequest.Status.PENDING_APPROVAL);
        }

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        writeChangeAudit(saved, user.getId(), "SUBMIT_FOR_APPROVAL",
                Map.of("status", oldStatus.name()),
                Map.of("status", saved.getStatus().name()));

        return toResponse(saved);
    }

    @Transactional
    public ChangeResponse addApproval(AppUser user, UUID orgId, UUID changeId, ChangeApprovalRequest request) {
        if (!isChangeContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can add change approvals");
        }

        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

        if (change.getChangeType() == ChangeRequest.ChangeType.STANDARD) {
            throw new IllegalStateException("STANDARD change requests do not require approvals");
        }

        AppUser approver = appUserRepository.findById(request.approverId())
                .orElseThrow(() -> new NotFoundException("Approver not found"));

        if (!canActAsApprover(change.getChangeType(), approver)) {
            throw new IllegalStateException("Approver does not have the required role for this change type");
        }

        ChangeApproval approval = new ChangeApproval();
        approval.setChangeRequest(change);
        approval.setApprover(approver);
        approval.setSequenceOrder(request.sequenceOrder());
        approval.setCreatedBy(user.getId());
        approval.setUpdatedBy(user.getId());
        changeApprovalRepository.save(approval);

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        writeChangeAudit(saved, user.getId(), "APPROVAL_ADDED", null,
                Map.of("approverId", approver.getId(),
                        "approverName", approver.getDisplayName(),
                        "sequenceOrder", request.sequenceOrder()));

        return toResponse(saved);
    }

    @Transactional
    public ChangeResponse approve(AppUser user, UUID orgId, UUID changeId, int sequenceOrder, String comment) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        ChangeRequest.Status oldStatus = change.getStatus();

        ChangeApproval approval = changeApprovalRepository
                .findByChangeRequestIdAndSequenceOrder(change.getId(), sequenceOrder)
                .orElseThrow(() -> new NotFoundException("Approval not found"));

        if (!isAllowedApprover(change, approval, user)) {
            throw new IllegalStateException("Only the designated approver or an administrator can approve this change");
        }

        if (!canActAsApprover(change.getChangeType(), user)) {
            throw new IllegalStateException("Approver does not have the required role for this change type");
        }

        if (change.getChangeType() == ChangeRequest.ChangeType.NORMAL) {
            List<ChangeApproval> approvals = changeApprovalRepository.findByChangeRequestIdOrderBySequenceOrderAsc(change.getId());
            for (ChangeApproval a : approvals) {
                if (a.getSequenceOrder() < approval.getSequenceOrder() && a.getStatus() != ChangeApproval.Status.APPROVED) {
                    throw new IllegalStateException("Previous approver has not approved");
                }
            }
        }

        approval.setStatus(ChangeApproval.Status.APPROVED);
        approval.setDecidedAt(OffsetDateTime.now());
        approval.setComment(comment);
        approval.setUpdatedBy(user.getId());
        changeApprovalRepository.save(approval);

        boolean allApproved = changeApprovalRepository
                .findByChangeRequestIdOrderBySequenceOrderAsc(change.getId())
                .stream()
                .allMatch(a -> a.getStatus() == ChangeApproval.Status.APPROVED);

        if (change.getChangeType() == ChangeRequest.ChangeType.EMERGENCY && allApproved) {
            change.setStatus(ChangeRequest.Status.IN_PROGRESS);
        } else if (allApproved) {
            change.setStatus(ChangeRequest.Status.APPROVED);
        }

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        Map<String, Object> afterApproved = new HashMap<>();
        afterApproved.put("status", saved.getStatus().name());
        if (comment != null && !comment.isBlank()) {
            afterApproved.put("comment", comment);
        }
        writeChangeAudit(saved, user.getId(), "APPROVED",
                Map.of("status", oldStatus.name()),
                afterApproved);

        return toResponse(saved);
    }

    @Transactional
    public ChangeResponse reject(AppUser user, UUID orgId, UUID changeId, int sequenceOrder, String comment) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        ChangeRequest.Status oldStatus = change.getStatus();

        ChangeApproval approval = changeApprovalRepository
                .findByChangeRequestIdAndSequenceOrder(change.getId(), sequenceOrder)
                .orElseThrow(() -> new NotFoundException("Approval not found"));

        if (!isAllowedApprover(change, approval, user)) {
            throw new IllegalStateException("Only the designated approver or an administrator can reject this change");
        }

        if (!canActAsApprover(change.getChangeType(), user)) {
            throw new IllegalStateException("Rejecting user does not have the required role for this change type");
        }

        approval.setStatus(ChangeApproval.Status.REJECTED);
        approval.setDecidedAt(OffsetDateTime.now());
        approval.setComment(comment);
        approval.setUpdatedBy(user.getId());
        changeApprovalRepository.save(approval);

        change.setStatus(ChangeRequest.Status.REJECTED);
        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(change);
        Map<String, Object> afterRejected = new HashMap<>();
        afterRejected.put("status", saved.getStatus().name());
        if (comment != null && !comment.isBlank()) {
            afterRejected.put("comment", comment);
        }
        writeChangeAudit(saved, user.getId(), "REJECTED",
                Map.of("status", oldStatus.name()),
                afterRejected);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ChangeCalendarResponse getCalendar(UUID orgId, OffsetDateTime from, OffsetDateTime to) {
        List<ChangeRequest> scheduled = changeRequestRepository.findByOrgIdAndStatusIn(orgId,
                List.of(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS));

        List<ChangeCalendarResponse.ChangeCalendarItem> items = scheduled.stream()
                .filter(c -> c.getPlannedStart() != null && c.getPlannedEnd() != null)
                .filter(c -> !c.getPlannedEnd().isBefore(from) && !c.getPlannedStart().isAfter(to))
                .map(c -> new ChangeCalendarResponse.ChangeCalendarItem(
                        c.getId(),
                        c.getNumber(),
                        c.getTitle(),
                        c.getLocation() != null ? c.getLocation().getId() : null,
                        c.getLocation() != null ? c.getLocation().getName() : null,
                        c.getPlannedStart(),
                        c.getPlannedEnd()))
                .toList();

        List<ChangeCalendarResponse.Conflict> conflicts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ChangeCalendarResponse.ChangeCalendarItem a = items.get(i);
                ChangeCalendarResponse.ChangeCalendarItem b = items.get(j);
                boolean overlap = a.plannedStart().isBefore(b.plannedEnd()) && b.plannedStart().isBefore(a.plannedEnd());
                boolean sameLocation = a.locationId() != null && a.locationId().equals(b.locationId());
                if (overlap && sameLocation) {
                    conflicts.add(new ChangeCalendarResponse.Conflict(a.id(), b.id()));
                }
            }
        }

        return new ChangeCalendarResponse(items, conflicts);
    }

    @Transactional(readOnly = true)
    public List<com.alignedcardio.itsm.api.auth.AuditLogResponse> listActivity(UUID orgId, UUID changeId) {
        changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        return auditLogRepository
                .findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(orgId, "CHANGE_REQUEST", changeId)
                .stream()
                .map(l -> new com.alignedcardio.itsm.api.auth.AuditLogResponse(
                        l.getId(), l.getActorUserId(), l.getAction(), l.getEntityType(),
                        l.getEntityId(), l.getBeforeState(), l.getAfterState(),
                        l.getIpAddress(), l.getCreatedAt()))
                .toList();
    }

    private Map<String, Object> changeAuditState(ChangeRequest change) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("number", change.getNumber());
        state.put("title", change.getTitle());
        state.put("description", change.getDescription());
        state.put("changeType", change.getChangeType() == null ? null : change.getChangeType().name());
        state.put("risk", change.getRisk() == null ? null : change.getRisk().name());
        state.put("status", change.getStatus() == null ? null : change.getStatus().name());
        state.put("plannedStart", change.getPlannedStart() == null ? null : change.getPlannedStart().toString());
        state.put("plannedEnd", change.getPlannedEnd() == null ? null : change.getPlannedEnd().toString());
        state.put("rollbackPlan", change.getRollbackPlan());
        state.put("postImplementationReview", change.getPostImplementationReview());
        state.put("requestedById", change.getRequestedBy() == null ? null : change.getRequestedBy().getId());
        state.put("requestedByName", change.getRequestedBy() == null ? null : change.getRequestedBy().getDisplayName());
        state.put("linkedProblemId", change.getLinkedProblem() == null ? null : change.getLinkedProblem().getId());
        state.put("linkedProblemNumber", change.getLinkedProblem() == null ? null : change.getLinkedProblem().getNumber());
        state.put("locationId", change.getLocation() == null ? null : change.getLocation().getId());
        state.put("locationName", change.getLocation() == null ? null : change.getLocation().getName());
        return state;
    }

    private void writeChangeFieldUpdateAudit(ChangeRequest change, UUID actorId, Map<String, Object> beforeState, boolean excludeStatus) {
        Map<String, Object> afterState = changeAuditState(change);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (String key : afterState.keySet()) {
            if (excludeStatus && "status".equals(key)) {
                continue;
            }
            if (!java.util.Objects.equals(beforeState.get(key), afterState.get(key))) {
                before.put(key, beforeState.get(key));
                after.put(key, afterState.get(key));
            }
        }
        if (!after.isEmpty()) {
            writeChangeAudit(change, actorId, "UPDATE", before, after);
        }
    }

    private void writeChangeAudit(ChangeRequest change, UUID actorId, String action,
                                Map<String, Object> beforeState, Map<String, Object> afterState) {
        try {
            AuditLog log = new AuditLog();
            log.setOrgId(change.getOrgId());
            log.setActorUserId(actorId);
            log.setAction(action);
            log.setEntityType("CHANGE_REQUEST");
            log.setEntityId(change.getId());
            log.setBeforeState(beforeState == null ? null : objectMapper.writeValueAsString(beforeState));
            log.setAfterState(afterState == null ? null : objectMapper.writeValueAsString(afterState));
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write change request audit log for {}", change.getId(), e);
        }
    }

    private void validateChangeTransition(AppUser user, ChangeRequest change, ChangeRequest.Status newStatus) {
        if (newStatus == ChangeRequest.Status.CLOSED && !isChangeAdmin(user)) {
            throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can close a change request");
        }
    }

    private boolean isAllowedApprover(ChangeRequest change, ChangeApproval approval, AppUser user) {
        if (isChangeAdmin(user)) {
            return true;
        }
        UUID designatedApproverId = approval.getApprover() != null ? approval.getApprover().getId() : null;
        return designatedApproverId != null && designatedApproverId.equals(user.getId());
    }

    private boolean canActAsApprover(ChangeRequest.ChangeType type, AppUser user) {
        if (type == ChangeRequest.ChangeType.EMERGENCY) {
            return isChangeAdmin(user);
        }
        return hasAnyRole(user, "TEAM_LEAD", "ADMIN", "SUPER_ADMIN",
                "ROLE_TEAM_LEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean isChangeAdmin(AppUser user) {
        return hasAnyRole(user, "ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean isChangeContributor(AppUser user) {
        return hasAnyRole(user, "AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN",
                "ROLE_AGENT", "ROLE_TEAM_LEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean hasAnyRole(AppUser user, String... names) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        List<String> targets = List.of(names);
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(targets::contains);
    }

    private String generateChangeNumber() {
        Long next = ((Number) entityManager.createNativeQuery("SELECT nextval('change_number_seq')")
                .getSingleResult()).longValue();
        return "CR-" + next;
    }

    private ChangeResponse toResponse(ChangeRequest change) {
        List<ChangeApproval> approvals = changeApprovalRepository
                .findByChangeRequestIdOrderBySequenceOrderAsc(change.getId());

        return new ChangeResponse(
                change.getId(),
                change.getNumber(),
                change.getTitle(),
                change.getDescription(),
                change.getChangeType(),
                change.getRisk(),
                change.getStatus(),
                change.getRequestedBy() != null ? change.getRequestedBy().getId() : null,
                change.getRequestedBy() != null ? change.getRequestedBy().getDisplayName() : null,
                change.getPlannedStart(),
                change.getPlannedEnd(),
                change.getRollbackPlan(),
                change.getPostImplementationReview(),
                change.getLinkedProblem() != null ? change.getLinkedProblem().getId() : null,
                change.getLocation() != null ? change.getLocation().getId() : null,
                change.getLocation() != null ? change.getLocation().getName() : null,
                approvals.stream()
                        .sorted(Comparator.comparingInt(ChangeApproval::getSequenceOrder))
                        .map(a -> new ChangeApprovalResponse(
                                a.getId(),
                                a.getApprover().getId(),
                                a.getApprover().getDisplayName(),
                                a.getSequenceOrder(),
                                a.getStatus(),
                                a.getDecidedAt(),
                                a.getComment()))
                        .toList(),
                change.getCreatedAt()
        );
    }
}
