package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.BurndownSnapshotResponse;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.Sprint;
import com.alignedcardio.itsm.entity.SprintBurndownSnapshot;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.repository.IssueRepository;
import com.alignedcardio.itsm.repository.SprintBurndownSnapshotRepository;
import com.alignedcardio.itsm.repository.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BurndownService {

    private final SprintBurndownSnapshotRepository snapshotRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;

    public BurndownService(SprintBurndownSnapshotRepository snapshotRepository,
                           SprintRepository sprintRepository,
                           IssueRepository issueRepository) {
        this.snapshotRepository = snapshotRepository;
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public void takeSnapshot(UUID sprintId, OffsetDateTime snapshotDate) {
        Sprint sprint = sprintRepository.findById(sprintId).orElse(null);
        if (sprint == null) {
            return;
        }

        List<Issue> issues = issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprintId);
        int totalPoints = 0;
        int remainingPoints = 0;
        int openIssues = 0;

        for (Issue issue : issues) {
            Integer points = issue.getStoryPoints();
            if (points == null) points = 0;
            totalPoints += points;
            if (issue.getWorkflowStatus() == null || issue.getWorkflowStatus().getCategory() != WorkflowStatus.Category.DONE) {
                remainingPoints += issue.getRemainingPoints() != null ? issue.getRemainingPoints() : points;
                openIssues++;
            }
        }

        SprintBurndownSnapshot snapshot = new SprintBurndownSnapshot();
        snapshot.setOrgId(sprint.getOrgId());
        snapshot.setSprint(sprint);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setTotalPoints(totalPoints);
        snapshot.setRemainingPoints(remainingPoints);
        snapshot.setOpenIssues(openIssues);
        snapshotRepository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<BurndownSnapshotResponse> getBurndown(UUID orgId, UUID sprintId) {
        return snapshotRepository.findBySprintIdOrderBySnapshotDateAsc(sprintId)
                .stream().map(this::toResponse).toList();
    }

    private BurndownSnapshotResponse toResponse(SprintBurndownSnapshot s) {
        return new BurndownSnapshotResponse(
                s.getId(),
                s.getSprint().getId(),
                s.getSnapshotDate(),
                s.getTotalPoints(),
                s.getRemainingPoints(),
                s.getOpenIssues()
        );
    }
}
