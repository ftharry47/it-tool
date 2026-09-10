package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.SprintCompleteRequest;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.Project;
import com.alignedcardio.itsm.entity.Sprint;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.repository.IssueRepository;
import com.alignedcardio.itsm.repository.ProjectRepository;
import com.alignedcardio.itsm.repository.SprintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private SprintService sprintService;

    private Sprint activeSprint() {
        Project project = new Project();
        project.setId(UUID.randomUUID());

        Sprint s = new Sprint();
        s.setId(UUID.randomUUID());
        s.setProject(project);
        s.setStatus(Sprint.Status.ACTIVE);
        return s;
    }

    private WorkflowStatus status(WorkflowStatus.Category category) {
        WorkflowStatus st = new WorkflowStatus();
        st.setId(UUID.randomUUID());
        st.setCategory(category);
        st.setName(category.name());
        return st;
    }

    @Test
    void completeSprintMovesIncompleteIssuesToBacklogAndKeepsDoneIssues() {
        Sprint sprint = activeSprint();
        WorkflowStatus done = status(WorkflowStatus.Category.DONE);
        WorkflowStatus inProgress = status(WorkflowStatus.Category.IN_PROGRESS);

        Issue doneIssue = new Issue();
        doneIssue.setSprint(sprint);
        doneIssue.setWorkflowStatus(done);

        Issue incompleteIssue = new Issue();
        incompleteIssue.setSprint(sprint);
        incompleteIssue.setWorkflowStatus(inProgress);

        when(sprintRepository.findById(sprint.getId())).thenReturn(Optional.of(sprint));
        when(issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprint.getId()))
                .thenReturn(List.of(doneIssue, incompleteIssue));
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.complete(UUID.randomUUID(), UUID.randomUUID(), sprint.getId(),
                new SprintCompleteRequest(SprintCompleteRequest.Destination.BACKLOG, null));

        assertNull(incompleteIssue.getSprint(), "Incomplete issue should return to backlog (sprint_id = null)");
        assertSame(sprint, doneIssue.getSprint(), "Done issue should keep original sprint_id");
        assertEquals(Sprint.Status.COMPLETED, sprint.getStatus());
    }

    @Test
    void completeSprintMovesIncompleteIssuesToNextSprintAndKeepsDoneIssues() {
        Sprint sprint = activeSprint();
        Sprint nextSprint = activeSprint();
        nextSprint.setStatus(Sprint.Status.PLANNING);

        WorkflowStatus done = status(WorkflowStatus.Category.DONE);
        WorkflowStatus inProgress = status(WorkflowStatus.Category.IN_PROGRESS);

        Issue doneIssue = new Issue();
        doneIssue.setSprint(sprint);
        doneIssue.setWorkflowStatus(done);

        Issue incompleteIssue = new Issue();
        incompleteIssue.setSprint(sprint);
        incompleteIssue.setWorkflowStatus(inProgress);

        when(sprintRepository.findById(sprint.getId())).thenReturn(Optional.of(sprint));
        when(sprintRepository.findById(nextSprint.getId())).thenReturn(Optional.of(nextSprint));
        when(issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprint.getId()))
                .thenReturn(List.of(doneIssue, incompleteIssue));
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.complete(UUID.randomUUID(), UUID.randomUUID(), sprint.getId(),
                new SprintCompleteRequest(SprintCompleteRequest.Destination.NEXT_SPRINT, nextSprint.getId()));

        assertSame(nextSprint, incompleteIssue.getSprint(), "Incomplete issue should move to next sprint");
        assertSame(sprint, doneIssue.getSprint(), "Done issue should keep original sprint_id");
    }
}
