package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Category;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentLink;
import com.alignedcardio.itsm.entity.IncidentWatcher;
import com.alignedcardio.itsm.entity.*;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.event.IncidentStatusChangedEvent;
import com.alignedcardio.itsm.event.IncidentAssignedEvent;
import com.alignedcardio.itsm.event.IncidentPriorityChangedEvent;
import com.alignedcardio.itsm.repository.*;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.util.PhoneNumbers;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentService {

    private static final Logger logger = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final PriorityRepository priorityRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final IncidentWatcherRepository watcherRepository;
    private final IncidentLinkRepository linkRepository;
    private final IncidentCommentRepository commentRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;
    private final SlaEngine slaEngine;
    private final SlaInstanceRepository slaInstanceRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final NotificationService notificationService;
    private final com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    // Fixed global support-tier chain (seeded in V38): L1 -> L2 -> L3.
    private static final UUID TIER_L1_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID TIER_L2_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID TIER_L3_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final List<UUID> TIER_CHAIN = List.of(TIER_L1_ID, TIER_L2_ID, TIER_L3_ID);

    public IncidentService(IncidentRepository incidentRepository,
                           PriorityRepository priorityRepository,
                           CategoryRepository categoryRepository,
                           LocationRepository locationRepository,
                           AppUserRepository appUserRepository,
                           TeamRepository teamRepository,
                           TeamMemberRepository teamMemberRepository,
                           IncidentWatcherRepository watcherRepository,
                           IncidentLinkRepository linkRepository,
                           IncidentCommentRepository commentRepository,
                           AuditLogRepository auditLogRepository,
                           EntityManager entityManager,
                           SlaEngine slaEngine,
                           SlaInstanceRepository slaInstanceRepository,
                           TimeEntryRepository timeEntryRepository,
                           NotificationService notificationService,
                           com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder,
                           ApplicationEventPublisher eventPublisher,
                           ObjectMapper objectMapper,
                           AuditLogService auditLogService) {
        this.incidentRepository = incidentRepository;
        this.priorityRepository = priorityRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.watcherRepository = watcherRepository;
        this.linkRepository = linkRepository;
        this.commentRepository = commentRepository;
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
        this.slaEngine = slaEngine;
        this.slaInstanceRepository = slaInstanceRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> list(UUID orgId, boolean showDeleted) {
        List<Incident> incidents = incidentRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .filter(i -> showDeleted ? i.getDeletedAt() != null : i.getDeletedAt() == null)
                .toList();
        return toSummaries(incidents);
    }

    // Tickets the agent previously owned (audit ASSIGN/REASSIGN/AUTO_ESCALATE_TIER
    // history), even if now reassigned, resolved, or closed. Deleted tickets are
    // excluded from the dashboard history.
    @Transactional(readOnly = true)
    public List<IncidentSummary> recentlyWorkedOn(AppUser user) {
        Set<UUID> ids = auditLogRepository
                .findIncidentAssigneeHistory(user.getOrgId(), user.getId().toString())
                .stream()
                .map(AuditLog::getEntityId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return toSummaries(incidentRepository
                .findByOrgIdAndIdInOrderByCreatedAtDesc(user.getOrgId(), ids)
                .stream()
                .filter(i -> i.getDeletedAt() == null)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listByReporter(UUID orgId, UUID reporterId) {
        return toSummaries(incidentRepository.findByOrgIdAndRequesterIdOrderByCreatedAtDesc(orgId, reporterId));
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listFiltered(UUID orgId, List<Incident.Status> statuses, UUID assigneeId, int limit, boolean showDeleted) {
        return listFiltered(orgId, statuses, assigneeId, null, limit, showDeleted);
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listFiltered(UUID orgId, List<Incident.Status> statuses, UUID assigneeId, UUID teamId, int limit, boolean showDeleted) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Incident> incidents;
        if (teamId != null && statuses != null && !statuses.isEmpty()) {
            incidents = incidentRepository.findByOrgIdAndAssignmentTeam_IdAndStatusInOrderByCreatedAtDesc(orgId, teamId, statuses, pageable);
        } else if (assigneeId != null && statuses != null && !statuses.isEmpty()) {
            incidents = incidentRepository.findByOrgIdAndAssigneeIdAndStatusInOrderByCreatedAtDesc(orgId, assigneeId, statuses, pageable);
        } else if (statuses != null && !statuses.isEmpty()) {
            incidents = incidentRepository.findByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, statuses, pageable);
        } else {
            incidents = incidentRepository.findByOrgIdOrderByCreatedAtDesc(orgId);
        }
        return toSummaries(incidents.stream()
                .filter(i -> showDeleted ? i.getDeletedAt() != null : i.getDeletedAt() == null)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listUnassigned(UUID orgId, List<Incident.Status> statuses, int limit, boolean showDeleted) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Incident.Status> active = (statuses != null && !statuses.isEmpty()) ? statuses : List.of(
                Incident.Status.NEW,
                Incident.Status.IN_PROGRESS,
                Incident.Status.ON_HOLD,
                Incident.Status.REOPENED);
        List<Incident> incidents = incidentRepository.findByOrgIdAndAssigneeIsNullAndStatusInOrderByCreatedAtDesc(orgId, active, pageable);
        return toSummaries(incidents.stream()
                .filter(i -> showDeleted ? i.getDeletedAt() != null : i.getDeletedAt() == null)
                .toList());
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID orgId, UUID id) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        return toResponse(incident);
    }

    // Viewer-aware variant: closingNotes is staff-only (AGENT+), hidden from END_USER.
    @Transactional(readOnly = true)
    public IncidentResponse get(AppUser viewer, UUID orgId, UUID id) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        return toResponse(incident, isStaff(viewer), viewer);
    }

    @Transactional
    public IncidentResponse create(AppUser requester, IncidentCreateRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        Incident incident = new Incident();
        incident.setOrgId(requester.getOrgId());
        incident.setRequester(requester);
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        int impact = request.impact() != null ? request.impact() : 3;
        int urgency = request.urgency() != null ? request.urgency() : 3;
        incident.setImpact(impact);
        incident.setUrgency(urgency);
        Priority priority = request.priorityId() != null
                ? priorityRepository.findById(request.priorityId()).orElse(null)
                : null;
        if (priority != null) {
            incident.setPriority(priority);
        } else {
            incident.setPriority(resolvePriority(impact, urgency, requester.getOrgId()));
        }
        incident.setCategory(category);
        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(requester.getOrgId(), request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            incident.setLocation(location);
        }
        incident.setPhone(PhoneNumbers.normalize(request.phone()));
        incident.setStatus(Incident.Status.NEW);
        incident.setCreatedBy(requester.getId());
        incident.setUpdatedBy(requester.getId());

        incidentRepository.saveAndFlush(incident);
        entityManager.refresh(incident);

        slaEngine.onIncidentCreated(incident);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", incident.getId());
        eventData.put("number", incident.getNumber());
        eventData.put("title", incident.getTitle());
        eventData.put("status", incident.getStatus().name());
        eventData.put("priority", incident.getPriority() != null ? incident.getPriority().getName() : null);
        eventData.put("category", incident.getCategory().getName());
        eventData.put("requesterId", incident.getRequester().getId());
        eventData.put("requesterFirstName", firstName(incident.getRequester().getDisplayName()));
        eventPublisher.publishEvent(new IncidentCreatedEvent(
                incident.getOrgId(),
                incident.getId(),
                eventData));

        // Broadcast to org admins when the incident has no assignee yet. This is a
        // broadcast-at-scale tradeoff for small orgs; revisit if org size grows.
        if (incident.getAssignee() == null) {
            notifyAdminsForAssignment(incident.getOrgId(), incident, requester.getDisplayName());
        }

        return toResponse(incident, true, requester);
    }

    @Transactional
    public IncidentResponse update(AppUser updater, UUID orgId, UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        Map<String, Object> beforeState = incidentAuditState(incident);

        incident.setTitle(request.title());
        incident.setDescription(request.description());

        if (request.priorityId() != null) {
            if (!canManagePriority(updater)) {
                throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can change priority");
            }
            Priority priority = priorityRepository.findById(request.priorityId())
                    .orElseThrow(() -> new NotFoundException("Priority not found"));
            UUID currentPriorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
            if (!Objects.equals(currentPriorityId, priority.getId())) {
                if (incident.getAssignee() != null) {
                    throw new IllegalStateException("Priority is locked after first assignment; use Escalate");
                }
                incident.setPriority(priority);
                slaEngine.onPriorityChanged(incident);
            }
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            incident.setCategory(category);
        }

        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            incident.setLocation(location);
        }

        if (request.assigneeId() != null) {
            if (!canManagePriority(updater)) {
                throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can assign or reassign incidents");
            }
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            UUID currentAssigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();
            if (!Objects.equals(currentAssigneeId, assignee.getId())) {
                incident.setAssignee(assignee);
                writeIncidentAudit(incident, updater.getId(),
                        currentAssigneeId == null ? "ASSIGN" : "REASSIGN",
                        beforeState, incidentAuditState(incident));
            }
        }

        setResolutionTimestamps(incident);
        incident.setUpdatedBy(updater.getId());
        slaEngine.onStatusChanged(incident);

        writeFieldUpdateAudit(incident, updater.getId(), beforeState);

        return toResponse(incident, true, updater);
    }

    @Transactional
    public IncidentResponse superAdminUpdate(AppUser updater, UUID orgId, UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        Map<String, Object> assignmentBeforeState = incidentAuditState(incident);

        incident.setTitle(request.title());
        incident.setDescription(request.description());

        Incident.Status newStatus = Incident.Status.valueOf(request.status());
        boolean statusChanged = false;
        if (incident.getStatus() != newStatus) {
            incident.setStatus(newStatus);
            statusChanged = true;
        }

        boolean priorityChanged = false;
        if (request.impact() != null && request.urgency() != null) {
            incident.setImpact(request.impact());
            incident.setUrgency(request.urgency());
            if (request.priorityId() == null && incident.getAssignee() == null) {
                incident.setPriority(resolvePriority(request.impact(), request.urgency(), orgId));
                priorityChanged = true;
            }
        }

        if (request.priorityId() != null) {
            if (!canManagePriority(updater)) {
                throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can change priority");
            }
            Priority priority = priorityRepository.findById(request.priorityId())
                    .orElseThrow(() -> new NotFoundException("Priority not found"));
            UUID currentPriorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
            if (!Objects.equals(currentPriorityId, priority.getId())) {
                if (incident.getAssignee() != null) {
                    throw new IllegalStateException("Priority is locked after first assignment; use Escalate");
                }
                incident.setPriority(priority);
                priorityChanged = true;
            }
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            incident.setCategory(category);
        }

        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            incident.setLocation(location);
        }

        UUID previousAssigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();
        AppUser newAssignee = null;
        if (request.assigneeId() != null) {
            newAssignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            incident.setAssignee(newAssignee);
            syncAssignmentTeam(incident, newAssignee);
        } else {
            incident.setAssignee(null);
            incident.setAssignmentTeam(null);
        }
        boolean assigneeChanged = !Objects.equals(previousAssigneeId, request.assigneeId());
        if (assigneeChanged) {
            resetEstimateLock(incident);
        }
        if (assigneeChanged) {
            String assignmentAction = previousAssigneeId == null
                    ? "ASSIGN"
                    : request.assigneeId() == null ? "UNASSIGN" : "REASSIGN";
            writeIncidentAudit(incident, updater.getId(), assignmentAction,
                    assignmentBeforeState, incidentAuditState(incident));
        }

        if (statusChanged) {
            setResolutionTimestamps(incident);
        }
        incident.setUpdatedBy(updater.getId());
        incident.setUpdatedAt(OffsetDateTime.now());

        Incident saved = incidentRepository.save(incident);

        if (statusChanged) {
            slaEngine.onStatusChanged(saved);
        }
        if (priorityChanged) {
            slaEngine.onPriorityChanged(saved);
        }
        if (assigneeChanged && newAssignee != null) {
            publishAssignment(saved, updater, newAssignee);
        }

        writeFieldUpdateAudit(saved, updater.getId(), assignmentBeforeState);

        return toResponse(saved, true, updater);
    }

    @Transactional
    public SlaInstanceResponse getSla(UUID orgId, UUID incidentId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        return slaInstanceRepository.findByIncidentIdAndOrgId(incident.getId(), orgId)
                .map(si -> new SlaInstanceResponse(
                        si.getId(),
                        incident.getId(),
                        si.getPolicy().getName(),
                        si.getResponseDueAt(),
                        si.getResolutionDueAt(),
                        si.getResponseMetAt(),
                        si.getResolutionMetAt(),
                        si.getPausedAt(),
                        si.getTotalPausedMinutes(),
                        si.getBreachStatus()
                ))
                .orElse(null);
    }

    @Transactional
    public IncidentResponse updateStatus(UUID updatedBy, UUID orgId, UUID id, Incident.Status newStatus) {
        AppUser updater = appUserRepository.findById(updatedBy)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return updateStatus(updater, orgId, id, newStatus, null);
    }

    @Transactional
    public IncidentResponse updateStatus(AppUser updater, UUID orgId, UUID id, Incident.Status newStatus) {
        return updateStatus(updater, orgId, id, newStatus, null);
    }

    @Transactional
    public IncidentResponse updateStatus(AppUser updater, UUID orgId, UUID id, Incident.Status newStatus, String closingNotes) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        // Only ADMIN/SUPER_ADMIN or the currently assigned agent may transition
        // status via the API. The UUID-based overload (automation) skips this.
        requireAdminOrAssignee(updater, incident, "transition incident status");

        IncidentStatusMachine.validate(incident.getStatus(), newStatus);
        if (newStatus == Incident.Status.REOPENED) {
            if (!canManagePriority(updater)) {
                throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can reopen an incident");
            }
            if (closingNotes == null || closingNotes.isBlank()) {
                throw new IllegalStateException("A comment is required when reopening an incident");
            }
        }
        if (newStatus == Incident.Status.CLOSED) {
            if (closingNotes == null || closingNotes.isBlank()) {
                throw new IllegalStateException("Closing notes are required when closing an incident");
            }
            incident.setClosingNotes(closingNotes.trim());
        }
        String oldStatus = incident.getStatus().name();
        incident.setStatus(newStatus);
        setResolutionTimestamps(incident);
        incident.setUpdatedBy(updater.getId());
        if (newStatus == Incident.Status.REOPENED) {
            resetEstimateLock(incident);
        }

        Incident saved = incidentRepository.save(incident);

        // Stop/resume/pause the incident SLA clock based on the new status.
        slaEngine.onStatusChanged(saved);

        if (newStatus == Incident.Status.REOPENED) {
            writeIncidentAudit(saved, updater.getId(), "REOPEN",
                    Map.of("status", oldStatus),
                    Map.of("status", saved.getStatus().name(), "reason", closingNotes.trim()));
        } else {
            writeIncidentAudit(saved, updater.getId(), "STATUS",
                    Map.of("status", oldStatus),
                    Map.of("status", saved.getStatus().name()));
        }

        eventPublisher.publishEvent(new IncidentStatusChangedEvent(
                saved.getOrgId(),
                saved.getId(),
                Map.of(
                        "id", saved.getId(),
                        "number", saved.getNumber(),
                        "title", saved.getTitle(),
                        "oldStatus", oldStatus,
                        "newStatus", saved.getStatus().name(),
                        "actorName", updater.getDisplayName(),
                        "requesterId", saved.getRequester().getId(),
                        "requesterFirstName", firstName(saved.getRequester().getDisplayName()))));

        notifyWatchers(saved, updater, "INCIDENT_UPDATE", Map.of(
                "number", saved.getNumber(),
                "title", saved.getTitle(),
                "oldStatus", oldStatus,
                "newStatus", saved.getStatus().name(),
                "actorName", updater.getDisplayName(),
                "entityType", "INCIDENT",
                "entityId", saved.getId()));

        return toResponse(saved, true, updater);
    }

    @Transactional
    public IncidentResponse assign(UUID updatedBy, UUID orgId, UUID id, UUID assigneeId) {
        AppUser updater = appUserRepository.findById(updatedBy)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return assign(updater, orgId, id, assigneeId, null);
    }

    @Transactional
    public IncidentResponse assign(AppUser updater, UUID orgId, UUID id, UUID assigneeId) {
        return assign(updater, orgId, id, assigneeId, null);
    }

    @Transactional
    public IncidentResponse assign(AppUser updater, UUID orgId, UUID id, UUID assigneeId, UUID priorityId) {
        if (!canManagePriority(updater)) {
            throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can assign or reassign incidents");
        }

        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        AppUser assignee = appUserRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        Map<String, Object> beforeState = incidentAuditState(incident);
        boolean firstAssignment = incident.getAssignee() == null;
        boolean priorityChanged = false;
        String oldPriorityName = incident.getPriority() == null ? null : incident.getPriority().getName();

        if (priorityId != null) {
            if (!firstAssignment) {
                throw new IllegalStateException("Priority is locked after first assignment; use Escalate");
            }
            Priority priority = loadActivePriority(orgId, priorityId);
            UUID currentPriorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
            if (!Objects.equals(currentPriorityId, priority.getId())) {
                incident.setPriority(priority);
                priorityChanged = true;
            }
        }

        boolean assigneeChanged = incident.getAssignee() == null
                || !assignee.getId().equals(incident.getAssignee().getId());
        incident.setAssignee(assignee);
        syncAssignmentTeam(incident, assignee);
        if (assigneeChanged) {
            resetEstimateLock(incident);
        }
        if (incident.getStatus() == Incident.Status.NEW
                || incident.getStatus() == Incident.Status.REOPENED
                || incident.getStatus() == Incident.Status.ON_HOLD) {
            incident.setStatus(Incident.Status.IN_PROGRESS);
        }
        incident.setUpdatedBy(updater.getId());

        Incident saved = incidentRepository.save(incident);

        if (priorityChanged) {
            slaEngine.onPriorityChanged(saved);
            publishPriorityChanged(saved, oldPriorityName,
                    saved.getPriority() == null ? null : saved.getPriority().getName(), null, updater);
        }

        writeIncidentAudit(saved, updater.getId(), firstAssignment ? "ASSIGN" : "REASSIGN",
                beforeState, incidentAuditState(saved));
        publishAssignment(saved, updater, assignee);

        return toResponse(saved, true, updater);
    }

    @Transactional
    public IncidentResponse escalate(AppUser updater, UUID orgId, UUID id, UUID priorityId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Escalation reason is required");
        }

        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        requireAdminOrAssignee(updater, incident, "escalate priority");
        if (incident.getAssignee() == null) {
            throw new IllegalStateException("Priority can be escalated only after the incident is assigned");
        }

        Priority currentPriority = incident.getPriority();
        if (currentPriority == null) {
            throw new IllegalStateException("Incident has no current priority to escalate");
        }

        Priority newPriority = loadActivePriority(orgId, priorityId);
        if (newPriority.getId().equals(currentPriority.getId())
                || newPriority.getDisplayOrder() >= currentPriority.getDisplayOrder()) {
            throw new IllegalStateException("Escalation must select a higher priority");
        }

        Map<String, Object> beforeState = incidentAuditState(incident);
        String trimmedReason = reason.trim();
        String oldPriorityName = currentPriority.getName();

        incident.setPriority(newPriority);
        incident.setUpdatedBy(updater.getId());
        Incident saved = incidentRepository.save(incident);

        slaEngine.onPriorityChanged(saved);

        Map<String, Object> beforePriority = new LinkedHashMap<>();
        if (oldPriorityName != null) {
            beforePriority.put("priorityName", oldPriorityName);
        }
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("priorityName", newPriority.getName());
        afterState.put("reason", trimmedReason);
        writeIncidentAudit(saved, updater.getId(), "ESCALATE_PRIORITY", beforePriority, afterState);
        publishPriorityChanged(saved, oldPriorityName, newPriority.getName(), trimmedReason, updater);

        Map<String, Object> contentPayload = new LinkedHashMap<>();
        contentPayload.put("number", saved.getNumber());
        contentPayload.put("title", saved.getTitle());
        contentPayload.put("oldPriority", oldPriorityName);
        contentPayload.put("newPriority", newPriority.getName());
        contentPayload.put("reason", trimmedReason);
        contentPayload.put("actorName", updater.getDisplayName());
        contentPayload.put("entityType", "INCIDENT");
        contentPayload.put("entityId", saved.getId());
        notifyWatchers(saved, updater, "INCIDENT_PRIORITY_CHANGED", contentPayload);
        notifyUser(saved.getAssignee(), updater, saved, "INCIDENT_PRIORITY_CHANGED", contentPayload);
        notifyUser(saved.getRequester(), updater, saved, "INCIDENT_PRIORITY_CHANGED", contentPayload);

        return toResponse(saved, true, updater);
    }

    // Manual tier escalation: moves the incident to the immediate next support tier
    // (L1 -> L2 -> L3). Blocked until the incident is assigned to a tier team.
    @Transactional
    public IncidentResponse escalateTier(AppUser updater, UUID orgId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Escalation reason is required");
        }

        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        requireAdminOrAssignee(updater, incident, "escalate incidents");

        Team current = incident.getAssignmentTeam();
        if (current == null || !TIER_CHAIN.contains(current.getId())) {
            throw new IllegalStateException("Assign this incident to a support tier before it can be escalated");
        }
        int nextIndex = TIER_CHAIN.indexOf(current.getId()) + 1;
        if (nextIndex >= TIER_CHAIN.size()) {
            throw new IllegalStateException("Incident is already at the highest support tier");
        }
        Team nextTier = teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, TIER_CHAIN.get(nextIndex))
                .orElseThrow(() -> new NotFoundException("Next support tier team not found"));

        Map<String, Object> beforeState = incidentAuditState(incident);
        String trimmedReason = reason.trim();
        String oldTeamName = current.getName();

        incident.setAssignmentTeam(nextTier);
        // Clear the assignee: they belonged to the previous tier. A blank
        // assignee makes the "needs reassignment" state explicit and removes
        // the old agent's assignee-scoped rights (status transition, estimate).
        incident.setAssignee(null);
        resetEstimateLock(incident);
        incident.setUpdatedBy(updater.getId());
        Incident saved = incidentRepository.save(incident);

        Map<String, Object> beforeTier = new LinkedHashMap<>();
        if (oldTeamName != null) {
            beforeTier.put("assignmentTeamName", oldTeamName);
        }
        // Record who the ticket was escalated AWAY from so the transition
        // freeze stays scoped to that agent instead of blocking every
        // future assignee. beforeState was captured pre-clear above.
        beforeTier.put("assigneeId", beforeState.get("assigneeId"));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("assignmentTeamName", nextTier.getName());
        afterState.put("reason", trimmedReason);
        writeIncidentAudit(saved, updater.getId(), "ESCALATE_TIER", beforeTier, afterState);

        Map<String, Object> contentPayload = new LinkedHashMap<>();
        contentPayload.put("number", saved.getNumber());
        contentPayload.put("title", saved.getTitle());
        contentPayload.put("oldTierName", oldTeamName);
        contentPayload.put("newTierName", nextTier.getName());
        contentPayload.put("reason", trimmedReason);
        contentPayload.put("actorName", updater.getDisplayName());
        contentPayload.put("entityType", "INCIDENT");
        contentPayload.put("entityId", saved.getId());
        for (TeamMember member : teamMemberRepository.findByTeamId(nextTier.getId())) {
            notifyUser(member.getUser(), updater, saved, "INCIDENT_TIER_ESCALATED", contentPayload);
        }

        return toResponse(saved, true, updater);
    }

    // Keeps incident.assignmentTeam in sync with the assignee's L1/L2/L3 membership.
    // Null when the assignee belongs to none of the three tier teams.
    private void syncAssignmentTeam(Incident incident, AppUser assignee) {
        Team tier = null;
        if (assignee != null) {
            for (TeamMember membership : teamMemberRepository.findByUserId(assignee.getId())) {
                if (TIER_CHAIN.contains(membership.getTeamId())) {
                    tier = teamRepository
                            .findByOrgIdAndIdAndDeletedAtIsNull(incident.getOrgId(), membership.getTeamId())
                            .orElse(null);
                    break;
                }
            }
        }
        incident.setAssignmentTeam(tier);
    }

    private boolean isStaff(AppUser user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(name -> List.of("AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN",
                        "ROLE_AGENT", "ROLE_TEAM_LEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN").contains(name));
    }

    @Transactional
    public IncidentResponse assignTeam(UUID updatedBy, UUID orgId, UUID id, UUID teamId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        if (teamId != null) {
            Team team = teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId)
                    .orElseThrow(() -> new NotFoundException("Team not found"));
            incident.setAssignmentTeam(team);
        } else {
            incident.setAssignmentTeam(null);
        }
        incident.setUpdatedBy(updatedBy);
        incident.setUpdatedAt(OffsetDateTime.now());
        return toResponse(incidentRepository.save(incident));
    }

    @Transactional
    public void updateField(UUID orgId, UUID updatedBy, UUID incidentId, String field, String value) {
        if ("status".equalsIgnoreCase(field)) {
            throw new IllegalStateException("SET_FIELD does not support status; use SET_STATUS to enforce the state machine");
        }

        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        switch (field.toLowerCase()) {
            case "assigneeid" -> {
                UUID previousAssigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();
                Map<String, Object> beforeState = incidentAuditState(incident);
                if (value == null || value.isBlank()) {
                    incident.setAssignee(null);
                } else {
                    UUID assigneeId = UUID.fromString(value);
                    AppUser assignee = appUserRepository.findById(assigneeId)
                            .orElseThrow(() -> new NotFoundException("Assignee not found"));
                    incident.setAssignee(assignee);
                }
                UUID newAssigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();
                if (!Objects.equals(previousAssigneeId, newAssigneeId)) {
                    String action = previousAssigneeId == null
                            ? "ASSIGN"
                            : newAssigneeId == null ? "UNASSIGN" : "REASSIGN";
                    writeIncidentAudit(incident, updatedBy, action, beforeState, incidentAuditState(incident));
                }
            }
            case "priorityid" -> {
                if (incident.getAssignee() != null) {
                    throw new IllegalStateException("Priority is locked after first assignment; use Escalate");
                }
                UUID priorityId = UUID.fromString(value);
                Priority priority = loadActivePriority(orgId, priorityId);
                UUID currentPriorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
                if (!Objects.equals(currentPriorityId, priority.getId())) {
                    incident.setPriority(priority);
                    slaEngine.onPriorityChanged(incident);
                }
            }
            case "categoryid" -> {
                UUID categoryId = UUID.fromString(value);
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new NotFoundException("Category not found"));
                incident.setCategory(category);
            }
            default -> throw new IllegalStateException("Unsupported SET_FIELD target: " + field);
        }

        incident.setUpdatedBy(updatedBy);
        incident.setUpdatedAt(OffsetDateTime.now());
        incidentRepository.save(incident);
    }

    private Priority loadActivePriority(UUID orgId, UUID priorityId) {
        Priority priority = priorityRepository.findById(priorityId)
                .orElseThrow(() -> new NotFoundException("Priority not found"));
        if (!orgId.equals(priority.getOrgId()) || priority.getStatus() != Priority.Status.ACTIVE) {
            throw new NotFoundException("Priority not found");
        }
        return priority;
    }

    private Map<String, Object> incidentAuditState(Incident incident) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("title", incident.getTitle());
        state.put("description", incident.getDescription());
        state.put("status", incident.getStatus() == null ? null : incident.getStatus().name());
        state.put("categoryId", incident.getCategory() == null ? null : incident.getCategory().getId());
        state.put("categoryName", incident.getCategory() == null ? null : incident.getCategory().getName());
        state.put("locationId", incident.getLocation() == null ? null : incident.getLocation().getId());
        state.put("locationName", incident.getLocation() == null ? null : incident.getLocation().getName());
        state.put("assigneeId", incident.getAssignee() == null ? null : incident.getAssignee().getId());
        state.put("assigneeName", incident.getAssignee() == null ? null : incident.getAssignee().getDisplayName());
        state.put("priorityId", incident.getPriority() == null ? null : incident.getPriority().getId());
        state.put("priorityName", incident.getPriority() == null ? null : incident.getPriority().getName());
        state.put("assignmentTeamId", incident.getAssignmentTeam() == null ? null : incident.getAssignmentTeam().getId());
        state.put("assignmentTeamName", incident.getAssignmentTeam() == null ? null : incident.getAssignmentTeam().getName());
        return state;
    }

    // Writes an UPDATE audit entry when any non-assignment field changed.
    // Assignee changes are already audited as ASSIGN/REASSIGN/UNASSIGN.
    private void writeFieldUpdateAudit(Incident incident, UUID actorId, Map<String, Object> beforeState) {
        Map<String, Object> afterState = incidentAuditState(incident);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (String key : afterState.keySet()) {
            if ("assigneeId".equals(key) || "assigneeName".equals(key)) {
                continue;
            }
            if (!Objects.equals(beforeState.get(key), afterState.get(key))) {
                before.put(key, beforeState.get(key));
                after.put(key, afterState.get(key));
            }
        }
        if (!after.isEmpty()) {
            writeIncidentAudit(incident, actorId, "UPDATE", before, after);
        }
    }

    private void writeIncidentAudit(Incident incident, UUID actorId, String action,
                                    Map<String, Object> beforeState, Map<String, Object> afterState) {
        try {
            AuditLog log = new AuditLog();
            log.setOrgId(incident.getOrgId());
            log.setActorUserId(actorId);
            log.setAction(action);
            log.setEntityType("INCIDENT");
            log.setEntityId(incident.getId());
            log.setBeforeState(objectMapper.writeValueAsString(beforeState));
            log.setAfterState(objectMapper.writeValueAsString(afterState));
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write incident audit log for {}", incident.getId(), e);
        }
    }

    private void publishAssignment(Incident saved, AppUser updater, AppUser assignee) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", saved.getId());
        payload.put("number", saved.getNumber());
        payload.put("assigneeId", assignee.getId());
        payload.put("assigneeName", assignee.getDisplayName());
        payload.put("requesterId", saved.getRequester() == null ? null : saved.getRequester().getId());

        eventPublisher.publishEvent(new IncidentAssignedEvent(saved.getOrgId(), saved.getId(), payload));

        if (!assignee.getId().equals(updater.getId())) {
            try {
                Map<String, Object> contentPayload = new LinkedHashMap<>();
                contentPayload.put("number", saved.getNumber());
                contentPayload.put("title", saved.getTitle());
                contentPayload.put("priority", saved.getPriority() != null ? saved.getPriority().getName() : "");
                contentPayload.put("status", saved.getStatus().name());
                contentPayload.put("location", saved.getLocation() != null ? saved.getLocation().getName() : "");
                contentPayload.put("actorName", updater.getDisplayName());
                contentPayload.put("assigneeFirstName", firstName(assignee.getDisplayName()));
                contentPayload.put("entityType", "INCIDENT");
                contentPayload.put("entityId", saved.getId());
                var content = notificationTemplateBuilder.forEvent("INCIDENT_ASSIGNED", contentPayload);
                notificationService.send(new NotificationRequest(
                        saved.getOrgId(),
                        assignee.getId(),
                        "INCIDENT_ASSIGNED",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "INCIDENT",
                        saved.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send assignment notification to {}", assignee.getId(), e);
            }
        }
    }

    private String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "there";
        return displayName.trim().split("\\s+")[0];
    }

    private void publishPriorityChanged(Incident saved, String oldPriority, String newPriority, String reason, AppUser updater) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", saved.getId());
        payload.put("number", saved.getNumber());
        payload.put("title", saved.getTitle());
        payload.put("oldPriority", oldPriority);
        payload.put("newPriority", newPriority);
        payload.put("reason", reason);
        payload.put("actorName", updater.getDisplayName());
        payload.put("requesterId", saved.getRequester() == null ? null : saved.getRequester().getId());
        payload.put("requesterFirstName", firstName(saved.getRequester() == null ? null : saved.getRequester().getDisplayName()));
        payload.put("assigneeId", saved.getAssignee() == null ? null : saved.getAssignee().getId());

        eventPublisher.publishEvent(new IncidentPriorityChangedEvent(saved.getOrgId(), saved.getId(), payload));
    }

    private void notifyUser(AppUser recipient, AppUser actor, Incident incident,
                            String type, Map<String, ?> payload) {
        if (recipient == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        try {
            Map<String, Object> userPayload = new java.util.HashMap<>(payload);
            userPayload.put("recipientFirstName", firstName(recipient.getDisplayName()));
            var content = notificationTemplateBuilder.forEvent(type, userPayload);
            notificationService.send(new NotificationRequest(
                    incident.getOrgId(),
                    recipient.getId(),
                    type,
                    content.inAppSubject(),
                    content.inAppBody(),
                    "INCIDENT",
                    incident.getId(),
                    null,
                    content));
        } catch (Exception e) {
            logger.warn("Failed to send {} notification to {}", type, recipient.getId(), e);
        }
    }

    private void notifyAdminsForAssignment(UUID orgId, Incident incident, String requesterName) {
        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                orgId, List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("number", incident.getNumber());
            payload.put("title", incident.getTitle());
            payload.put("requesterName", requesterName);
            payload.put("priority", incident.getPriority() != null ? incident.getPriority().getName() : "");
            payload.put("entityType", "INCIDENT");
            payload.put("entityId", incident.getId());
            try {
                var content = notificationTemplateBuilder.forEvent("INCIDENT_CREATED_UNASSIGNED", payload);
                notificationService.send(new NotificationRequest(
                        orgId,
                        admin.getId(),
                        "INCIDENT_CREATED_UNASSIGNED",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "INCIDENT",
                        incident.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send INCIDENT_CREATED_UNASSIGNED to {}", admin.getId(), e);
            }
        }
    }

    private Priority resolvePriority(int impact, int urgency, UUID orgId) {
        String priorityName = ImpactUrgencyMatrix.resolve(impact, urgency);
        return priorityRepository.findByOrgIdAndName(orgId, priorityName)
                .orElseGet(() -> priorityRepository
                        .findByOrgIdAndStatusOrderByDisplayOrderAsc(orgId, Priority.Status.ACTIVE)
                        .stream()
                        .findFirst()
                        .orElse(null));
    }

    // ADMIN/SUPER_ADMIN may always escalate; other staff only when the
    // incident is currently assigned to them.
    private void requireAdminOrAssignee(AppUser user, Incident incident, String action) {
        if (canManagePriority(user)) {
            return;
        }
        boolean isAssignee = incident.getAssignee() != null
                && incident.getAssignee().getId().equals(user.getId());
        if (!isAssignee) {
            throw new IllegalStateException(
                    "Only ADMIN, SUPER_ADMIN, or the assigned agent can " + action);
        }
    }

    private boolean canManagePriority(AppUser user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(name -> "ADMIN".equals(name) || "SUPER_ADMIN".equals(name) || "ROLE_ADMIN".equals(name) || "ROLE_SUPER_ADMIN".equals(name));
    }

    private void setResolutionTimestamps(Incident incident) {
        if (incident.getStatus() == Incident.Status.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(OffsetDateTime.now());
        }
        if (incident.getStatus() == Incident.Status.CLOSED && incident.getClosedAt() == null) {
            incident.setClosedAt(OffsetDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public List<PriorityOption> priorities(UUID orgId) {
        return priorityRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(orgId, Priority.Status.ACTIVE).stream()
                .map(p -> new PriorityOption(p.getId(), p.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> categories(UUID orgId) {
        return categoryRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(orgId, Category.Status.ACTIVE).stream()
                .map(c -> new CategoryOption(c.getId(), c.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> search(UUID orgId, String query) {
        return search(orgId, query, null, 20);
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> search(UUID orgId, String query, UUID requesterId, int limit) {
        List<Incident> incidents = requesterId == null
                ? incidentRepository.searchByText(orgId, query, limit)
                : incidentRepository.searchByTextForRequester(orgId, requesterId, query, limit);
        return toSummaries(incidents);
    }

    @Transactional(readOnly = true)
    public List<IncidentWatcherResponse> listWatchers(UUID orgId, UUID incidentId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        return watcherRepository.findByIncidentIdAndDeletedAtIsNull(incident.getId()).stream()
                .map(w -> new IncidentWatcherResponse(
                        w.getId(),
                        w.getUser().getId(),
                        w.getUser().getDisplayName()
                ))
                .toList();
    }

    @Transactional
    public IncidentWatcherResponse addWatcher(UUID orgId, UUID incidentId, UUID userId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return watcherRepository.findByIncidentIdAndUserId(incident.getId(), user.getId())
                .map(w -> new IncidentWatcherResponse(w.getId(), w.getUser().getId(), w.getUser().getDisplayName()))
                .orElseGet(() -> {
                    IncidentWatcher watcher = new IncidentWatcher();
                    watcher.setOrgId(orgId);
                    watcher.setIncident(incident);
                    watcher.setUser(user);
                    watcher.setCreatedBy(user.getId());
                    watcher.setUpdatedBy(user.getId());
                    watcherRepository.save(watcher);
                    return new IncidentWatcherResponse(watcher.getId(), user.getId(), user.getDisplayName());
                });
    }

    @Transactional
    public void removeWatcher(UUID orgId, UUID incidentId, UUID userId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        watcherRepository.findByIncidentIdAndUserId(incident.getId(), userId)
                .ifPresent(watcherRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<IncidentLinkResponse> listLinks(UUID orgId, UUID incidentId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        return linkRepository.findByFromIncidentIdAndDeletedAtIsNull(incident.getId()).stream()
                .map(l -> toIncidentLinkResponse(l, orgId))
                .toList();
    }

    @Transactional
    public IncidentLinkResponse addLink(UUID orgId, UUID fromIncidentId, LinkCreateRequest request, UUID actorId) {
        Incident from = incidentRepository.findByOrgIdAndId(orgId, fromIncidentId)
                .orElseThrow(() -> new NotFoundException("Source incident not found"));
        Incident to = incidentRepository.findByOrgIdAndId(orgId, request.toIncidentId())
                .orElseThrow(() -> new NotFoundException("Target incident not found"));

        IncidentLink link = new IncidentLink();
        link.setOrgId(orgId);
        link.setFromIncident(from);
        link.setToIncident(to);
        link.setLinkType(IncidentLink.LinkType.valueOf(request.linkType()));
        link.setCreatedBy(actorId);
        link.setUpdatedBy(actorId);

        linkRepository.save(link);

        return new IncidentLinkResponse(
                link.getId(),
                to.getId(),
                link.getLinkType().name(),
                to.getNumber(),
                to.getTitle(),
                to.getStatus().name()
        );
    }

    private IncidentLinkResponse toIncidentLinkResponse(IncidentLink link, UUID orgId) {
        UUID toIncidentId = link.getToIncident() == null ? null : link.getToIncident().getId();
        Incident to = toIncidentId == null
                ? null
                : incidentRepository.findByOrgIdAndId(orgId, toIncidentId).orElse(null);
        return new IncidentLinkResponse(
                link.getId(),
                toIncidentId,
                link.getLinkType().name(),
                to == null ? null : to.getNumber(),
                to == null ? null : to.getTitle(),
                to == null ? null : to.getStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public List<EscalationEntry> recentEscalations(UUID orgId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 50)));
        List<AuditLog> entries = auditLogRepository
                .findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                        orgId, "INCIDENT",
                        List.of("ESCALATE_PRIORITY", "ESCALATE_TIER", "REOPEN"),
                        pageable)
                .getContent();

        Map<UUID, Incident> incidentsById = incidentRepository
                .findAllById(entries.stream().map(AuditLog::getEntityId).toList())
                .stream()
                .collect(Collectors.toMap(Incident::getId, i -> i));
        Map<UUID, String> actorNames = appUserRepository
                .findAllById(entries.stream().map(AuditLog::getActorUserId).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));

        return entries.stream().map(entry -> {
            Incident incident = incidentsById.get(entry.getEntityId());
            return new EscalationEntry(
                    entry.getEntityId(),
                    incident != null ? incident.getNumber() : null,
                    incident != null ? incident.getTitle() : null,
                    entry.getAction(),
                    actorNames.getOrDefault(entry.getActorUserId(), "Unknown"),
                    escalationDetail(entry),
                    entry.getCreatedAt());
        }).toList();
    }

    private String escalationDetail(AuditLog entry) {
        try {
            Map<String, Object> before = entry.getBeforeState() != null
                    ? objectMapper.readValue(entry.getBeforeState(), Map.class) : Map.of();
            Map<String, Object> after = entry.getAfterState() != null
                    ? objectMapper.readValue(entry.getAfterState(), Map.class) : Map.of();
            String reason = after.get("reason") instanceof String r ? r : null;
            return switch (entry.getAction()) {
                case "ESCALATE_PRIORITY" -> {
                    String from = before.get("priorityName") instanceof String p ? p : "?";
                    String to = after.get("priorityName") instanceof String p ? p : "?";
                    yield from + " → " + to + (reason != null ? " — " + reason : "");
                }
                case "ESCALATE_TIER" -> reason != null ? "Tier escalation — " + reason : "Tier escalation";
                case "REOPEN" -> reason != null ? "Reopened — " + reason : "Reopened";
                default -> entry.getAction();
            };
        } catch (Exception e) {
            return entry.getAction();
        }
    }

    private List<IncidentSummary> toSummaries(List<Incident> incidents) {
        if (incidents.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = incidents.stream().map(Incident::getId).toList();
        Map<UUID, SlaInstance> slaByIncident = slaInstanceRepository
                .findByIncidentIdIn(ids)
                .stream()
                .collect(Collectors.toMap(si -> si.getIncident().getId(), si -> si, (a, b) -> a));
        Set<UUID> escalatedIds = ids.isEmpty()
                ? Set.of()
                : auditLogRepository
                        .findByOrgIdAndEntityTypeAndEntityIdInAndActionIn(
                                incidents.get(0).getOrgId(),
                                "INCIDENT",
                                ids,
                                List.of("ESCALATE_TIER", "AUTO_ESCALATE_TIER"))
                        .stream()
                        .map(AuditLog::getEntityId)
                        .collect(Collectors.toSet());
        return incidents.stream()
                .map(i -> toSummary(i, slaByIncident.get(i.getId()), escalatedIds.contains(i.getId())))
                .toList();
    }

    private IncidentSummary toSummary(Incident incident, SlaInstance sla, boolean hasBeenTierEscalated) {
        UUID priorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
        UUID categoryId = incident.getCategory() == null ? null : incident.getCategory().getId();
        UUID locationId = incident.getLocation() == null ? null : incident.getLocation().getId();
        UUID requesterId = incident.getRequester() == null ? null : incident.getRequester().getId();
        UUID assigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();

        Priority priority = priorityId == null ? null : priorityRepository.findById(priorityId).orElse(null);
        Category category = categoryId == null ? null : categoryRepository.findById(categoryId).orElse(null);
        Location location = locationId == null ? null : locationRepository.findById(locationId).orElse(null);
        AppUser requester = requesterId == null ? null : appUserRepository.findById(requesterId).orElse(null);
        AppUser assignee = assigneeId == null ? null : appUserRepository.findById(assigneeId).orElse(null);

        return new IncidentSummary(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getStatus().name(),
                priority == null ? null : priority.getName(),
                category == null ? null : category.getName(),
                location == null ? null : location.getName(),
                incident.getPhone(),
                requester == null ? null : requester.getDisplayName(),
                assignee == null ? null : assignee.getDisplayName(),
                assigneeId,
                incident.getCreatedAt(),
                sla != null && sla.getBreachStatus() != null ? sla.getBreachStatus().name() : null,
                sla != null ? sla.getResponseDueAt() : null,
                sla != null ? sla.getResolutionDueAt() : null,
                sla != null ? sla.getResponseMetAt() : null,
                sla != null ? sla.getResolutionMetAt() : null,
                hasBeenTierEscalated
        );
    }

    private IncidentResponse toResponse(Incident incident) {
        return toResponse(incident, true, null);
    }

    private IncidentResponse toResponse(Incident incident, boolean includeStaffFields) {
        return toResponse(incident, includeStaffFields, null);
    }

    private IncidentResponse toResponse(Incident incident, boolean includeStaffFields, AppUser viewer) {
        // Reload lazy/soft-deleted associations through repositories before mapping.
        // See AGENTS.md: "Reload associations before mapping to responses".
        UUID assigneeId = incident.getAssignee() == null ? null : incident.getAssignee().getId();
        UUID assignmentTeamId = incident.getAssignmentTeam() == null ? null : incident.getAssignmentTeam().getId();
        UUID requesterId = incident.getRequester() == null ? null : incident.getRequester().getId();
        UUID priorityId = incident.getPriority() == null ? null : incident.getPriority().getId();
        UUID categoryId = incident.getCategory() == null ? null : incident.getCategory().getId();
        UUID locationId = incident.getLocation() == null ? null : incident.getLocation().getId();

        AppUser assignee = assigneeId == null ? null : appUserRepository.findById(assigneeId).orElse(null);
        Team assignmentTeam = assignmentTeamId == null ? null : teamRepository.findById(assignmentTeamId).orElse(null);
        AppUser requester = requesterId == null ? null : appUserRepository.findById(requesterId).orElse(null);
        Priority priority = priorityId == null ? null : priorityRepository.findById(priorityId).orElse(null);
        Category category = categoryId == null ? null : categoryRepository.findById(categoryId).orElse(null);
        Location location = locationId == null ? null : locationRepository.findById(locationId).orElse(null);

        Integer totalLogged = timeEntryRepository
                .findByEntityTypeAndEntityIdAndDeletedAtIsNull("INCIDENT", incident.getId())
                .stream()
                .mapToInt(TimeEntry::getTimeSpentMinutes)
                .sum();
        boolean hasBeenTierEscalated = auditLogRepository.existsByOrgIdAndEntityTypeAndEntityIdAndActionIn(
                incident.getOrgId(),
                "INCIDENT",
                incident.getId(),
                List.of("ESCALATE_TIER", "AUTO_ESCALATE_TIER"));
        // Escalation freezes the agent it was escalated AWAY from — not every
        // future assignee. before_state->>assigneeId on the escalation audit
        // row identifies that agent; the incident-level flag stays for display.
        boolean tierEscalatedFromMe = viewer != null
                && auditLogRepository.existsTierEscalationAwayFrom(
                        incident.getOrgId(), incident.getId(), viewer.getId().toString());
        return new IncidentResponse(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getStatus().name(),
                priority == null ? null : priority.getName(),
                category == null ? null : category.getName(),
                requester == null ? null : requester.getDisplayName(),
                assignee == null ? null : assignee.getDisplayName(),
                assignee == null ? null : assignee.getId(),
                assignmentTeam == null ? null : assignmentTeam.getId(),
                assignmentTeam == null ? null : assignmentTeam.getName(),
                includeStaffFields ? incident.getClosingNotes() : null,
                location == null ? null : location.getName(),
                location == null ? null : location.getId(),
                incident.getPhone(),
                incident.getEstimatedMinutes(),
                totalLogged,
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                hasBeenTierEscalated,
                tierEscalatedFromMe
        );
    }

    @Transactional
    public IncidentResponse logTime(AppUser user, UUID orgId, UUID incidentId, int timeSpentMinutes, String description) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setOrgId(orgId);
        timeEntry.setEntityType("INCIDENT");
        timeEntry.setEntityId(incidentId);
        timeEntry.setUser(user);
        timeEntry.setTimeSpentMinutes(timeSpentMinutes);
        timeEntry.setDescription(description);
        timeEntry.setLoggedAt(OffsetDateTime.now());
        timeEntry.setCreatedBy(user.getId());
        timeEntry.setUpdatedBy(user.getId());
        timeEntryRepository.save(timeEntry);

        return toResponse(incident, true, user);
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> listTimeEntries(UUID orgId, UUID incidentId) {
        return timeEntryRepository.findByOrgIdAndEntityTypeAndEntityIdAndDeletedAtIsNull(orgId, "INCIDENT", incidentId).stream()
                .map(te -> new TimeEntryResponse(
                        te.getId(),
                        te.getTimeSpentMinutes(),
                        te.getDescription(),
                        te.getUser() != null ? te.getUser().getDisplayName() : null,
                        te.getLoggedAt()
                ))
                .toList();
    }

    @Transactional
    public IncidentResponse setEstimatedMinutes(AppUser user, UUID orgId, UUID incidentId, Integer estimatedMinutes) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        if (estimatedMinutes == null || estimatedMinutes < 0) {
            throw new IllegalStateException("A positive estimate in minutes is required");
        }
        boolean isAssignee = incident.getAssignee() != null
                && incident.getAssignee().getId().equals(user.getId());
        if (!isAssignee && !canManagePriority(user)) {
            throw new IllegalStateException("Only the assigned agent or an admin can set the estimate");
        }
        if (incident.getEstimateSetAt() != null) {
            throw new IllegalStateException(
                    "Estimate is locked for this assignment; it can be set again after escalation, reopen, or reassignment");
        }
        incident.setEstimatedMinutes(estimatedMinutes);
        incident.setEstimateSetAt(OffsetDateTime.now());
        incident.setEstimateSetById(user.getId());
        incident.setUpdatedBy(user.getId());
        incidentRepository.save(incident);
        return toResponse(incident, true, user);
    }

    // Clears the one-time estimate lock so the new owner gets a fresh
    // estimate opportunity. Called on genuine reassignment, tier
    // escalation, and reopen — never on a no-op update.
    private void resetEstimateLock(Incident incident) {
        incident.setEstimatedMinutes(null);
        incident.setEstimateSetAt(null);
        incident.setEstimateSetById(null);
    }

    @Transactional(readOnly = true)
    public List<com.alignedcardio.itsm.api.auth.AuditLogResponse> listActivity(UUID orgId, UUID incidentId) {
        incidentRepository.findByOrgIdAndId(orgId, incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        return auditLogRepository
                .findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(orgId, "INCIDENT", incidentId)
                .stream()
                .map(auditLogService::toResponse)
                .toList();
    }

    private void notifyWatchers(Incident incident, AppUser actor, String type, Map<String, ?> payload) {
        watcherRepository.findByIncidentIdAndDeletedAtIsNull(incident.getId()).forEach(w -> {
            if (w.getUser().getId().equals(actor.getId())) {
                return;
            }
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
}
