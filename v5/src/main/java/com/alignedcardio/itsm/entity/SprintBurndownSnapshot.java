package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sprint_burndown_snapshot")
public class SprintBurndownSnapshot extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @NotNull
    @Column(name = "snapshot_date", nullable = false)
    private OffsetDateTime snapshotDate;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "remaining_points", nullable = false)
    private int remainingPoints;

    @Column(name = "open_issues", nullable = false)
    private int openIssues;

    public Sprint getSprint() {
        return sprint;
    }

    public void setSprint(Sprint sprint) {
        this.sprint = sprint;
    }

    public OffsetDateTime getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(OffsetDateTime snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getRemainingPoints() {
        return remainingPoints;
    }

    public void setRemainingPoints(int remainingPoints) {
        this.remainingPoints = remainingPoints;
    }

    public int getOpenIssues() {
        return openIssues;
    }

    public void setOpenIssues(int openIssues) {
        this.openIssues = openIssues;
    }
}
