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
import com.alignedcardio.itsm.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final PriorityRepository priorityRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final IncidentWatcherRepository watcherRepository;
    private final IncidentLinkRepository linkRepository;
    private final EntityManager entityManager;
    private final SlaEngine slaEngine;
    private final SlaInstanceRepository slaInstanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentService(IncidentRepository incidentRepository,
                           PriorityRepository priorityRepository,
                           CategoryRepository categoryRepository,
                           AppUserRepository appUserRepository,
                           TeamRepository teamRepository,
                           IncidentWatcherRepository watcherRepository,
                           IncidentLinkRepository linkRepository,
                           EntityManager entityManager,
                           SlaEngine slaEngine,
                           SlaInstanceRepository slaInstanceRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.priorityRepository = priorityRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.watcherRepository = watcherRepository;
        this.linkRepository = linkRepository;
        this.entityManager = entityManager;
        this.slaEngine = slaEngine;
        this.slaInstanceRepository = slaInstanceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> list(UUID orgId) {
        return incidentRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID orgId, UUID id) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        return toResponse(incident);
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
        incident.setImpact(request.impact());
        incident.setUrgency(request.urgency());
        incident.setPriority(resolvePriority(request.impact(), request.urgency(), requester.getOrgId()));
        incident.setCategory(category);
        incident.setStatus(Incident.Status.NEW);
        incident.setCreatedBy(requester.getId());
        incident.setUpdatedBy(requester.getId());

        incidentRepository.saveAndFlush(incident);
        entityManager.refresh(incident);

        slaEngine.onIncidentCreated(incident);

        eventPublisher.publishEvent(new IncidentCreatedEvent(
                incident.getOrgId(),
                incident.getId(),
                Map.of(
                        "id", incident.getId(),
                        "number", incident.getNumber(),
                        "status", incident.getStatus().name(),
                        "priority", incident.getPriority().getName(),
                        "category", incident.getCategory().getName())));

        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse update(AppUser updater, UUID orgId, UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        incident.setTitle(request.title());
        incident.setDescription(request.description());

        if (request.priorityId() != null) {
            Priority priority = priorityRepository.findById(request.priorityId())
                    .orElseThrow(() -> new NotFoundException("Priority not found"));
            incident.setPriority(priority);
            slaEngine.onPriorityChanged(incident);
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            incident.setCategory(category);
        }

        if (request.assigneeId() != null) {
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            incident.setAssignee(assignee);
        } else {
            incident.setAssignee(null);
        }

        setResolutionTimestamps(incident);
        incident.setUpdatedBy(updater.getId());
        slaEngine.onStatusChanged(incident);

        return toResponse(incident);
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
        return updateStatus(updater, orgId, id, newStatus);
    }

    @Transactional
    public IncidentResponse updateStatus(AppUser updater, UUID orgId, UUID id, Incident.Status newStatus) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        IncidentStatusMachine.validate(incident.getStatus(), newStatus);
        String oldStatus = incident.getStatus().name();
        incident.setStatus(newStatus);
        setResolutionTimestamps(incident);
        incident.setUpdatedBy(updater.getId());

        Incident saved = incidentRepository.save(incident);

        eventPublisher.publishEvent(new IncidentStatusChangedEvent(
                saved.getOrgId(),
                saved.getId(),
                Map.of(
                        "id", saved.getId(),
                        "number", saved.getNumber(),
                        "oldStatus", oldStatus,
                        "newStatus", saved.getStatus().name())));

        return toResponse(saved);
    }

    @Transactional
    public IncidentResponse assign(UUID updatedBy, UUID orgId, UUID id, UUID assigneeId) {
        AppUser updater = appUserRepository.findById(updatedBy)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return assign(updater, orgId, id, assigneeId);
    }

    @Transactional
    public IncidentResponse assign(AppUser updater, UUID orgId, UUID id, UUID assigneeId) {
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        AppUser assignee = appUserRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        incident.setAssignee(assignee);
        if (incident.getStatus() == Incident.Status.NEW || incident.getStatus() == Incident.Status.REOPENED) {
            incident.setStatus(Incident.Status.IN_PROGRESS);
        }
        incident.setUpdatedBy(updater.getId());

        return toResponse(incident);
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
                if (value == null || value.isBlank()) {
                    incident.setAssignee(null);
                } else {
                    UUID assigneeId = UUID.fromString(value);
                    AppUser assignee = appUserRepository.findById(assigneeId)
                            .orElseThrow(() -> new NotFoundException("Assignee not found"));
                    incident.setAssignee(assignee);
                }
            }
            case "priorityid" -> {
                UUID priorityId = UUID.fromString(value);
                Priority priority = priorityRepository.findById(priorityId)
                        .orElseThrow(() -> new NotFoundException("Priority not found"));
                incident.setPriority(priority);
                slaEngine.onPriorityChanged(incident);
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

    private Priority resolvePriority(int impact, int urgency, UUID orgId) {
        String priorityName = ImpactUrgencyMatrix.resolve(impact, urgency);
        return priorityRepository.findByOrgIdAndName(orgId, priorityName)
                .orElseThrow(() -> new IllegalStateException("Priority not found for " + priorityName));
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
        return incidentRepository.searchByText(orgId, query).stream()
                .map(this::toSummary)
                .toList();
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
                .map(l -> new IncidentLinkResponse(
                        l.getId(),
                        l.getToIncident().getId(),
                        l.getLinkType().name(),
                        l.getToIncident().getNumber()
                ))
                .toList();
    }

    @Transactional
    public IncidentLinkResponse addLink(UUID orgId, UUID fromIncidentId, LinkCreateRequest request) {
        Incident from = incidentRepository.findByOrgIdAndId(orgId, fromIncidentId)
                .orElseThrow(() -> new NotFoundException("Source incident not found"));
        Incident to = incidentRepository.findByOrgIdAndId(orgId, request.toIncidentId())
                .orElseThrow(() -> new NotFoundException("Target incident not found"));

        IncidentLink link = new IncidentLink();
        link.setOrgId(orgId);
        link.setFromIncident(from);
        link.setToIncident(to);
        link.setLinkType(IncidentLink.LinkType.valueOf(request.linkType()));
        link.setCreatedBy(from.getCreatedBy());
        link.setUpdatedBy(from.getCreatedBy());

        linkRepository.save(link);

        return new IncidentLinkResponse(
                link.getId(),
                to.getId(),
                link.getLinkType().name(),
                to.getNumber()
        );
    }

    private IncidentSummary toSummary(Incident incident) {
        return new IncidentSummary(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getStatus().name(),
                Optional.ofNullable(incident.getPriority()).map(Priority::getName).orElse(null),
                Optional.ofNullable(incident.getCategory()).map(Category::getName).orElse(null),
                Optional.ofNullable(incident.getRequester()).map(AppUser::getDisplayName).orElse(null),
                Optional.ofNullable(incident.getAssignee()).map(AppUser::getDisplayName).orElse(null),
                incident.getCreatedAt()
        );
    }

    private IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getStatus().name(),
                Optional.ofNullable(incident.getPriority()).map(Priority::getName).orElse(null),
                Optional.ofNullable(incident.getCategory()).map(Category::getName).orElse(null),
                Optional.ofNullable(incident.getRequester()).map(AppUser::getDisplayName).orElse(null),
                Optional.ofNullable(incident.getAssignee()).map(AppUser::getDisplayName).orElse(null),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
