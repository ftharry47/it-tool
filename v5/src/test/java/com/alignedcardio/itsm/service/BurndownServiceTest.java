package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.BurndownSnapshotResponse;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.Sprint;
import com.alignedcardio.itsm.entity.SprintBurndownSnapshot;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.repository.IssueRepository;
import com.alignedcardio.itsm.repository.SprintBurndownSnapshotRepository;
import com.alignedcardio.itsm.repository.SprintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BurndownServiceTest {

    @Mock
    private SprintBurndownSnapshotRepository snapshotRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private BurndownService burndownService;

    @Test
    void takeSnapshotSavesPrecomputedMetrics() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setOrgId(UUID.randomUUID());

        WorkflowStatus done = new WorkflowStatus();
        done.setCategory(WorkflowStatus.Category.DONE);
        WorkflowStatus todo = new WorkflowStatus();
        todo.setCategory(WorkflowStatus.Category.TODO);

        Issue open = new Issue();
        open.setStoryPoints(5);
        open.setRemainingPoints(5);
        open.setWorkflowStatus(todo);

        Issue completed = new Issue();
        completed.setStoryPoints(3);
        completed.setRemainingPoints(3);
        completed.setWorkflowStatus(done);

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprintId))
                .thenReturn(List.of(open, completed));

        burndownService.takeSnapshot(sprintId, OffsetDateTime.now());

        ArgumentCaptor<SprintBurndownSnapshot> captor = ArgumentCaptor.forClass(SprintBurndownSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        SprintBurndownSnapshot saved = captor.getValue();

        assertEquals(8, saved.getTotalPoints());
        assertEquals(5, saved.getRemainingPoints());
        assertEquals(1, saved.getOpenIssues());
    }

    @Test
    void getBurndownReturnsPrecomputedSnapshots() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);

        SprintBurndownSnapshot s1 = new SprintBurndownSnapshot();
        s1.setSprint(sprint);
        s1.setSnapshotDate(OffsetDateTime.now());
        s1.setTotalPoints(10);
        s1.setRemainingPoints(10);
        s1.setOpenIssues(2);

        when(snapshotRepository.findBySprintIdOrderBySnapshotDateAsc(sprintId))
                .thenReturn(List.of(s1));

        List<BurndownSnapshotResponse> result = burndownService.getBurndown(UUID.randomUUID(), sprintId);

        assertEquals(1, result.size());
        assertEquals(10, result.get(0).totalPoints());
        assertEquals(2, result.get(0).openIssues());
        verify(issueRepository, never()).findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(any());
    }
}
