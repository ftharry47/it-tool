package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.change.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ChangeApproval;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.ChangeApprovalRepository;
import com.alignedcardio.itsm.repository.ChangeRequestRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ChangeService {

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeApprovalRepository changeApprovalRepository;
    private final AppUserRepository appUserRepository;
    private final ProblemRepository problemRepository;
    private final EntityManager entityManager;

    public ChangeService(ChangeRequestRepository changeRequestRepository,
                         ChangeApprovalRepository changeApprovalRepository,
                         AppUserRepository appUserRepository,
                         ProblemRepository problemRepository,
                         EntityManager entityManager) {
        this.changeRequestRepository = changeRequestRepository;
        this.changeApprovalRepository = changeApprovalRepository;
        this.appUserRepository = appUserRepository;
        this.problemRepository = problemRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ChangeResponse> list(UUID orgId) {
        return changeRequestRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChangeResponse create(AppUser user, UUID orgId, ChangeCreateRequest request) {
        ChangeRequest change = new ChangeRequest();
        change.setOrgId(orgId);
        change.setTitle(request.title());
        change.setDescription(request.description());
        change.setChangeType(request.changeType());
        change.setRisk(request.risk());
        change.setPlannedStart(request.plannedStart());
        change.setPlannedEnd(request.plannedEnd());
        change.setRollbackPlan(request.rollbackPlan());
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

        ChangeRequest saved = changeRequestRepository.save(change);
        entityManager.flush();
        entityManager.refresh(saved);

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
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

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

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        return toResponse(changeRequestRepository.save(change));
    }

    @Transactional
    public ChangeResponse updateStatus(AppUser user, UUID orgId, UUID id, ChangeRequest.Status newStatus) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

        ChangeStatusMachine.validate(change, newStatus);
        change.setStatus(newStatus);
        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        return toResponse(changeRequestRepository.save(change));
    }

    @Transactional
    public ChangeResponse submitForApproval(AppUser user, UUID orgId, UUID id) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

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

        return toResponse(changeRequestRepository.save(change));
    }

    @Transactional
    public ChangeResponse addApproval(AppUser user, UUID orgId, UUID changeId, ChangeApprovalRequest request) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

        AppUser approver = appUserRepository.findById(request.approverId())
                .orElseThrow(() -> new NotFoundException("Approver not found"));

        ChangeApproval approval = new ChangeApproval();
        approval.setChangeRequest(change);
        approval.setApprover(approver);
        approval.setSequenceOrder(request.sequenceOrder());
        approval.setCreatedBy(user.getId());
        approval.setUpdatedBy(user.getId());
        changeApprovalRepository.save(approval);

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        return toResponse(changeRequestRepository.save(change));
    }

    @Transactional
    public ChangeResponse approve(AppUser user, UUID orgId, UUID changeId, int sequenceOrder, String comment) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

        ChangeApproval approval = changeApprovalRepository
                .findByChangeRequestIdAndSequenceOrder(change.getId(), sequenceOrder)
                .orElseThrow(() -> new NotFoundException("Approval not found"));

        if (approval.getApprover().getId().equals(user.getId())) {
            throw new IllegalStateException("Cannot approve your own approval");
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

        if (allApproved) {
            change.setStatus(ChangeRequest.Status.APPROVED);
        } else if (change.getChangeType() == ChangeRequest.ChangeType.EMERGENCY) {
            change.setStatus(ChangeRequest.Status.IN_PROGRESS);
        }

        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        return toResponse(changeRequestRepository.save(change));
    }

    @Transactional
    public ChangeResponse reject(AppUser user, UUID orgId, UUID changeId, int sequenceOrder, String comment) {
        ChangeRequest change = changeRequestRepository.findByOrgIdAndId(orgId, changeId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));

        ChangeApproval approval = changeApprovalRepository
                .findByChangeRequestIdAndSequenceOrder(change.getId(), sequenceOrder)
                .orElseThrow(() -> new NotFoundException("Approval not found"));

        approval.setStatus(ChangeApproval.Status.REJECTED);
        approval.setDecidedAt(OffsetDateTime.now());
        approval.setComment(comment);
        approval.setUpdatedBy(user.getId());
        changeApprovalRepository.save(approval);

        change.setStatus(ChangeRequest.Status.REJECTED);
        change.setUpdatedBy(user.getId());
        change.setUpdatedAt(OffsetDateTime.now());

        return toResponse(changeRequestRepository.save(change));
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
                        c.getPlannedStart(),
                        c.getPlannedEnd()))
                .toList();

        List<ChangeCalendarResponse.Conflict> conflicts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ChangeCalendarResponse.ChangeCalendarItem a = items.get(i);
                ChangeCalendarResponse.ChangeCalendarItem b = items.get(j);
                if (a.plannedStart().isBefore(b.plannedEnd()) && b.plannedStart().isBefore(a.plannedEnd())) {
                    conflicts.add(new ChangeCalendarResponse.Conflict(a.id(), b.id()));
                }
            }
        }

        return new ChangeCalendarResponse(items, conflicts);
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
