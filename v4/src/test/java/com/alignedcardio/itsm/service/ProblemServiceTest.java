package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.problem.ProblemUpdateRequest;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.ProblemIncidentLinkRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemIncidentLinkRepository problemIncidentLinkRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProblemService problemService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
        return user;
    }

    private Problem resolvedProblem() {
        Problem problem = new Problem();
        problem.setId(UUID.randomUUID());
        problem.setOrgId(orgId);
        problem.setNumber("PROB-1");
        problem.setTitle("Test problem");
        problem.setStatus(Problem.Status.RESOLVED);
        return problem;
    }

    @Test
    void agentCannotCloseProblem() {
        Problem problem = resolvedProblem();
        when(problemRepository.findByOrgIdAndId(orgId, problem.getId())).thenReturn(Optional.of(problem));

        AppUser agent = userWithRole("AGENT");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> problemService.updateStatus(agent, orgId, problem.getId(), Problem.Status.CLOSED));
        assertEquals("Only ADMIN or SUPER_ADMIN can close a problem", ex.getMessage());
    }

    @Test
    void adminCanCloseProblem() {
        Problem problem = resolvedProblem();
        when(problemRepository.findByOrgIdAndId(orgId, problem.getId())).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        AppUser admin = userWithRole("ADMIN");

        var response = problemService.updateStatus(admin, orgId, problem.getId(), Problem.Status.CLOSED);

        assertEquals(Problem.Status.CLOSED, response.status());
    }

    @Test
    void agentCanMoveResolvedBackToInvestigating() {
        Problem problem = resolvedProblem();
        when(problemRepository.findByOrgIdAndId(orgId, problem.getId())).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        AppUser agent = userWithRole("AGENT");

        var response = problemService.updateStatus(agent, orgId, problem.getId(), Problem.Status.INVESTIGATING);

        assertEquals(Problem.Status.INVESTIGATING, response.status());
    }

    @Test
    void agentCanSetRootCauseAndWorkaround() {
        Problem problem = resolvedProblem();
        when(problemRepository.findByOrgIdAndId(orgId, problem.getId())).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        AppUser agent = userWithRole("AGENT");

        var response = problemService.update(agent, orgId, problem.getId(),
                new ProblemUpdateRequest(null, null, Problem.Status.INVESTIGATING, "Root cause", "Workaround", null));

        assertEquals("Root cause", response.rootCause());
        assertEquals("Workaround", response.workaround());
    }
}
