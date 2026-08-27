package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.problem.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ProblemIncidentLink;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.ProblemIncidentLinkRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemIncidentLinkRepository problemIncidentLinkRepository;
    private final IncidentRepository incidentRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    public ProblemService(ProblemRepository problemRepository,
                          ProblemIncidentLinkRepository problemIncidentLinkRepository,
                          IncidentRepository incidentRepository,
                          AppUserRepository appUserRepository,
                          EntityManager entityManager) {
        this.problemRepository = problemRepository;
        this.problemIncidentLinkRepository = problemIncidentLinkRepository;
        this.incidentRepository = incidentRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list(UUID orgId) {
        return problemRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProblemResponse create(AppUser user, UUID orgId, ProblemCreateRequest request) {
        Problem problem = new Problem();
        problem.setOrgId(orgId);
        problem.setTitle(request.title());
        problem.setDescription(request.description());
        problem.setCreatedBy(user.getId());
        problem.setUpdatedBy(user.getId());

        if (request.assigneeId() != null) {
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            problem.setAssignee(assignee);
        }

        Problem saved = problemRepository.save(problem);
        entityManager.flush();
        entityManager.refresh(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID orgId, UUID id) {
        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        return toResponse(problem);
    }

    @Transactional
    public ProblemResponse update(AppUser user, UUID orgId, UUID id, ProblemUpdateRequest request) {
        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));

        if (request.title() != null) problem.setTitle(request.title());
        if (request.description() != null) problem.setDescription(request.description());
        if (request.rootCause() != null) problem.setRootCause(request.rootCause());
        if (request.workaround() != null) problem.setWorkaround(request.workaround());

        if (request.assigneeId() != null) {
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            problem.setAssignee(assignee);
        }

        if (request.status() != null && request.status() != problem.getStatus()) {
            ProblemStatusMachine.validate(problem.getStatus(), request.status());
            setStatusTimestamps(problem, request.status());
            problem.setStatus(request.status());
        }

        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        return toResponse(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse updateStatus(AppUser user, UUID orgId, UUID id, Problem.Status newStatus) {
        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));

        ProblemStatusMachine.validate(problem.getStatus(), newStatus);
        setStatusTimestamps(problem, newStatus);
        problem.setStatus(newStatus);
        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        return toResponse(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse linkIncident(AppUser user, UUID orgId, UUID problemId, LinkIncidentRequest request) {
        Problem problem = problemRepository.findByOrgIdAndId(orgId, problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, request.incidentId())
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        if (problemIncidentLinkRepository.existsByProblemIdAndIncidentId(problem.getId(), incident.getId())) {
            throw new IllegalStateException("Incident already linked to this problem");
        }

        ProblemIncidentLink link = new ProblemIncidentLink();
        link.setProblemId(problem.getId());
        link.setIncidentId(incident.getId());
        problemIncidentLinkRepository.save(link);

        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        return toResponse(problemRepository.save(problem));
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listLinkedIncidents(UUID orgId, UUID problemId) {
        return problemIncidentLinkRepository.findByProblemId(problemId).stream()
                .map(ProblemIncidentLink::getIncidentId)
                .map(incidentRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toIncidentSummary)
                .toList();
    }

    private void setStatusTimestamps(Problem problem, Problem.Status newStatus) {
        if (newStatus == Problem.Status.RESOLVED) {
            problem.setResolvedAt(OffsetDateTime.now());
        } else if (newStatus == Problem.Status.CLOSED) {
            problem.setClosedAt(OffsetDateTime.now());
        } else if (newStatus == Problem.Status.INVESTIGATING || newStatus == Problem.Status.KNOWN_ERROR) {
            problem.setResolvedAt(null);
            problem.setClosedAt(null);
        }
    }

    private ProblemResponse toResponse(Problem problem) {
        return new ProblemResponse(
                problem.getId(),
                problem.getNumber(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getStatus(),
                problem.getRootCause(),
                problem.getWorkaround(),
                problem.getAssignee() != null ? problem.getAssignee().getId() : null,
                problem.getAssignee() != null ? problem.getAssignee().getDisplayName() : null,
                problem.getResolvedAt(),
                problem.getClosedAt(),
                problem.getCreatedAt()
        );
    }

    private IncidentSummary toIncidentSummary(Incident incident) {
        return new IncidentSummary(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getStatus().name(),
                incident.getPriority() != null ? incident.getPriority().getName() : null,
                incident.getCategory() != null ? incident.getCategory().getName() : null,
                incident.getRequester() != null ? incident.getRequester().getDisplayName() : null,
                incident.getAssignee() != null ? incident.getAssignee().getDisplayName() : null,
                incident.getCreatedAt()
        );
    }

    public record IncidentSummary(
            UUID id,
            Long number,
            String title,
            String status,
            String priority,
            String category,
            String requester,
            String assignee,
            OffsetDateTime createdAt
    ) {
    }
}
