package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Sprint;
import com.alignedcardio.itsm.repository.SprintRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class BurndownSnapshotJob implements Job {

    private final SprintRepository sprintRepository;
    private final BurndownService burndownService;

    public BurndownSnapshotJob(SprintRepository sprintRepository, BurndownService burndownService) {
        this.sprintRepository = sprintRepository;
        this.burndownService = burndownService;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Sprint> active = sprintRepository.findAll().stream()
                .filter(s -> s.getStatus() == Sprint.Status.ACTIVE)
                .toList();

        for (Sprint sprint : active) {
            burndownService.takeSnapshot(sprint.getId(), now);
        }
    }
}
