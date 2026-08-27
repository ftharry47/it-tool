package com.alignedcardio.itsm.config;

import com.alignedcardio.itsm.service.BurndownSnapshotJob;
import com.alignedcardio.itsm.service.SlaBreachMonitorJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail slaBreachMonitorJobDetail() {
        return JobBuilder.newJob(SlaBreachMonitorJob.class)
                .withIdentity("sla-breach-monitor")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger slaBreachMonitorTrigger() {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(5)
                .repeatForever();

        return TriggerBuilder.newTrigger()
                .forJob(slaBreachMonitorJobDetail())
                .withIdentity("sla-breach-monitor-trigger")
                .withSchedule(schedule)
                .build();
    }

    @Bean
    public JobDetail burndownSnapshotJobDetail() {
        return JobBuilder.newJob(BurndownSnapshotJob.class)
                .withIdentity("burndown-snapshot")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger burndownSnapshotTrigger() {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInHours(24)
                .repeatForever();

        return TriggerBuilder.newTrigger()
                .forJob(burndownSnapshotJobDetail())
                .withIdentity("burndown-snapshot-trigger")
                .withSchedule(schedule)
                .build();
    }
}
