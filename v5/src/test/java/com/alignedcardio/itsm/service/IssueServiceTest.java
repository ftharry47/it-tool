package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.BoardColumn;
import com.alignedcardio.itsm.api.project.IssueCreateRequest;
import com.alignedcardio.itsm.api.project.IssueResponse;
import com.alignedcardio.itsm.api.project.IssueStatusChangeRequest;
import com.alignedcardio.itsm.entity.*;
import com.alignedcardio.itsm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private IssueTypeRepository issueTypeRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowStatusRepository workflowStatusRepository;
    @Mock
    private WorkflowTransitionValidator workflowTransitionValidator;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private IssueService issueService;

    @Test
    void illegalDragAndDropStatusChangeIsRejected() {
        UUID orgId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID newStatusId = UUID.randomUUID();

        Project project = new Project();
        project.setId(UUID.randomUUID());

        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID());

        WorkflowStatus current = new WorkflowStatus();
        current.setId(UUID.randomUUID());
        current.setName("To Do");
        current.setCategory(WorkflowStatus.Category.TODO);
        current.setWorkflow(workflow);

        WorkflowStatus target = new WorkflowStatus();
        target.setId(newStatusId);
        target.setName("Done");
        target.setCategory(WorkflowStatus.Category.DONE);
        target.setWorkflow(workflow);

        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setProject(project);
        issue.setWorkflow(workflow);
        issue.setWorkflowStatus(current);

        when(issueRepository.findByProjectIdAndIdAndDeletedAtIsNull(orgId, issueId))
                .thenReturn(Optional.of(issue));
        when(workflowStatusRepository.findByWorkflowIdAndId(workflow.getId(), newStatusId))
                .thenReturn(Optional.of(target));
        doThrow(new IllegalStateException("Illegal workflow transition"))
                .when(workflowTransitionValidator).validate(workflow.getId(), current.getId(), target.getId());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> issueService.changeStatus(orgId, UUID.randomUUID(), issueId,
                        new IssueStatusChangeRequest(newStatusId)));
        assertTrue(ex.getMessage().contains("Illegal workflow transition"));
    }

    @Test
    void numberingUsesSequenceAndProducesDistinctKeys() {
        UUID orgId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setKey("PROJ");

        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID());

        WorkflowStatus status = new WorkflowStatus();
        status.setId(UUID.randomUUID());

        AppUser reporter = new AppUser();
        reporter.setId(createdBy);
        reporter.setDisplayName("Reporter");

        when(projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, project.getId()))
                .thenReturn(Optional.of(project));
        when(workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, workflow.getId()))
                .thenReturn(Optional.of(workflow));
        when(workflowStatusRepository.findByWorkflowIdAndId(workflow.getId(), status.getId()))
                .thenReturn(Optional.of(status));
        when(appUserRepository.findById(createdBy)).thenReturn(Optional.of(reporter));
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> {
            Issue saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Query query = mock(Query.class);
        AtomicLong counter = new AtomicLong(100);
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        when(query.getSingleResult()).thenAnswer(i -> counter.getAndIncrement());

        IssueCreateRequest request = new IssueCreateRequest(
                project.getId(), null, workflow.getId(), status.getId(),
                null, null, null, "Summary", null, null, null, null);

        IssueResponse first = issueService.create(orgId, createdBy, request);
        IssueResponse second = issueService.create(orgId, createdBy, request);

        assertEquals("PROJ-100", first.key());
        assertEquals("PROJ-101", second.key());

        // Two calls to the per-project sequence return different numbers, proving
        // the sequence (not a MAX+1 race) is the source of numbers.
        verify(entityManager, atLeast(2)).createNativeQuery(anyString());
    }

    @Test
    void perProjectNumberingIsIsolated() {
        UUID orgId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Project projectA = new Project();
        projectA.setId(UUID.randomUUID());
        projectA.setOrgId(orgId);
        projectA.setKey("PROJ");

        Project projectB = new Project();
        projectB.setId(UUID.randomUUID());
        projectB.setOrgId(orgId);
        projectB.setKey("OTHER");

        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID());

        WorkflowStatus status = new WorkflowStatus();
        status.setId(UUID.randomUUID());

        AppUser reporter = new AppUser();
        reporter.setId(createdBy);
        reporter.setDisplayName("Reporter");

        when(workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, workflow.getId()))
                .thenReturn(Optional.of(workflow));
        when(workflowStatusRepository.findByWorkflowIdAndId(workflow.getId(), status.getId()))
                .thenReturn(Optional.of(status));
        when(appUserRepository.findById(createdBy)).thenReturn(Optional.of(reporter));
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> {
            Issue saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // One counter per project sequence.
        Map<String, AtomicLong> counters = new HashMap<>();
        when(entityManager.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Query q = mock(Query.class);
            if (sql.contains("nextval")) {
                int open = sql.indexOf('\'');
                int close = sql.lastIndexOf('\'');
                String seq = sql.substring(open + 1, close);
                when(q.getSingleResult()).thenAnswer(i ->
                        counters.computeIfAbsent(seq, k -> new AtomicLong(1)).getAndIncrement());
            }
            return q;
        });

        // Create in Project A, then in Project B.
        when(projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, projectA.getId()))
                .thenReturn(Optional.of(projectA));
        IssueCreateRequest requestA = new IssueCreateRequest(
                projectA.getId(), null, workflow.getId(), status.getId(),
                null, null, null, "A", null, null, null, null);
        IssueResponse issueA = issueService.create(orgId, createdBy, requestA);

        when(projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, projectB.getId()))
                .thenReturn(Optional.of(projectB));
        IssueCreateRequest requestB = new IssueCreateRequest(
                projectB.getId(), null, workflow.getId(), status.getId(),
                null, null, null, "B", null, null, null, null);
        IssueResponse issueB = issueService.create(orgId, createdBy, requestB);

        assertEquals("PROJ-1", issueA.key());
        assertEquals("OTHER-1", issueB.key());
    }

    @Test
    void createWithSprintEpicParentAssigneePersistsRelationships() {
        UUID orgId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setKey("PROJ");

        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID());

        WorkflowStatus status = new WorkflowStatus();
        status.setId(UUID.randomUUID());

        AppUser reporter = new AppUser();
        reporter.setId(createdBy);

        AppUser assignee = new AppUser();
        assignee.setId(UUID.randomUUID());

        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());

        Issue epic = new Issue();
        epic.setId(UUID.randomUUID());
        epic.setOrgId(orgId);

        Issue parent = new Issue();
        parent.setId(UUID.randomUUID());
        parent.setOrgId(orgId);

        when(projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, project.getId()))
                .thenReturn(Optional.of(project));
        when(workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, workflow.getId()))
                .thenReturn(Optional.of(workflow));
        when(workflowStatusRepository.findByWorkflowIdAndId(workflow.getId(), status.getId()))
                .thenReturn(Optional.of(status));
        when(appUserRepository.findById(createdBy)).thenReturn(Optional.of(reporter));
        when(appUserRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));
        when(issueRepository.findById(epic.getId())).thenReturn(Optional.of(epic));
        when(issueRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(entityManager.getReference(Sprint.class, sprint.getId())).thenReturn(sprint);
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> {
            Issue saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(7L);

        IssueCreateRequest request = new IssueCreateRequest(
                project.getId(), null, workflow.getId(), status.getId(),
                sprint.getId(), epic.getId(), parent.getId(),
                "Story", null, assignee.getId(), 5, null);

        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        issueService.create(orgId, createdBy, request);
        verify(issueRepository).save(captor.capture());
        Issue saved = captor.getValue();

        assertEquals(sprint.getId(), saved.getSprint().getId());
        assertEquals(epic.getId(), saved.getEpic().getId());
        assertEquals(parent.getId(), saved.getParentIssue().getId());
        assertEquals(assignee.getId(), saved.getAssignee().getId());
        assertEquals(5, saved.getStoryPoints());
    }

    @Test
    void backlogExcludesSprintIssues() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID());

        WorkflowStatus status = new WorkflowStatus();
        status.setId(UUID.randomUUID());
        status.setName("To Do");
        status.setCategory(WorkflowStatus.Category.TODO);

        AppUser reporter = new AppUser();
        reporter.setId(UUID.randomUUID());

        Issue inBacklog = new Issue();
        inBacklog.setOrgId(orgId);
        inBacklog.setProject(project);
        inBacklog.setKey("PROJ-1");
        inBacklog.setSummary("backlog item");
        inBacklog.setWorkflow(workflow);
        inBacklog.setWorkflowStatus(status);
        inBacklog.setReporter(reporter);
        inBacklog.setSprint(null);

        when(issueRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId))
                .thenReturn(List.of(inBacklog));

        List<IssueResponse> result = issueService.backlog(orgId, projectId);

        assertEquals(1, result.size());
        assertEquals(projectId, result.get(0).projectId());
        assertTrue(result.stream().allMatch(i -> i.sprintId() == null));
    }

    @Test
    void boardByProjectGroupsByWorkflowStatus() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        AppUser reporter = new AppUser();
        reporter.setId(UUID.randomUUID());

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);

        WorkflowStatus todo = new WorkflowStatus();
        todo.setId(UUID.randomUUID());
        todo.setWorkflow(workflow);
        todo.setName("To Do");
        todo.setCategory(WorkflowStatus.Category.TODO);
        todo.setDisplayOrder(0);

        WorkflowStatus inProgress = new WorkflowStatus();
        inProgress.setId(UUID.randomUUID());
        inProgress.setWorkflow(workflow);
        inProgress.setName("In Progress");
        inProgress.setCategory(WorkflowStatus.Category.IN_PROGRESS);
        inProgress.setDisplayOrder(1);

        Issue a = new Issue();
        a.setProject(project);
        a.setKey("PROJ-1");
        a.setSummary("todo task");
        a.setReporter(reporter);
        a.setWorkflow(workflow);
        a.setWorkflowStatus(todo);

        Issue b = new Issue();
        b.setProject(project);
        b.setKey("PROJ-2");
        b.setSummary("in progress task");
        b.setReporter(reporter);
        b.setWorkflow(workflow);
        b.setWorkflowStatus(inProgress);

        when(issueRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId))
                .thenReturn(List.of(a, b));
        when(workflowStatusRepository.findByWorkflowIdOrderByDisplayOrderAsc(workflowId))
                .thenReturn(List.of(todo, inProgress));

        List<BoardColumn> result = issueService.boardByProject(orgId, projectId, workflowId, null);

        assertEquals(2, result.size());
        assertEquals("To Do", result.get(0).status());
        assertEquals("In Progress", result.get(1).status());
        assertEquals(1, result.get(0).issues().size());
        assertEquals(1, result.get(1).issues().size());
    }
}
