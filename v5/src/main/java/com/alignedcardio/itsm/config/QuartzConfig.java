package com.alignedcardio.itsm.config;

import com.alignedcardio.itsm.service.BurndownSnapshotJob;
import com.alignedcardio.itsm.service.FulfillmentReminderJob;
import com.alignedcardio.itsm.service.SlaBreachMonitorJob;
import com.alignedcardio.itsm.service.notification.NotificationDigestJob;
import com.alignedcardio.itsm.service.reporting.AgentPerformanceReportJob;
import org.quartz.CronScheduleBuilder;
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

    @Bean
    public JobDetail notificationDigestJobDetail() {
        return JobBuilder.newJob(NotificationDigestJob.class)
                .withIdentity("notification-digest")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger notificationDigestTrigger() {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInHours(1)
                .repeatForever();

        return TriggerBuilder.newTrigger()
                .forJob(notificationDigestJobDetail())
                .withIdentity("notification-digest-trigger")
                .withSchedule(schedule)
                .build();
    }

    @Bean
    public JobDetail fulfillmentReminderJobDetail() {
        return JobBuilder.newJob(FulfillmentReminderJob.class)
                .withIdentity("fulfillment-reminder")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger fulfillmentReminderTrigger() {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInHours(24)
                .repeatForever();

        return TriggerBuilder.newTrigger()
                .forJob(fulfillmentReminderJobDetail())
                .withIdentity("fulfillment-reminder-trigger")
                .withSchedule(schedule)
                .build();
    }

    @Bean
    public JobDetail agentPerformanceReportJobDetail() {
        return JobBuilder.newJob(AgentPerformanceReportJob.class)
                .withIdentity("agent-performance-report")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger agentPerformanceReportTrigger() {
        // 00:15 on the 1st of each month — generates last month's reports.
        return TriggerBuilder.newTrigger()
                .forJob(agentPerformanceReportJobDetail())
                .withIdentity("agent-performance-report-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 15 0 1 * ?"))
                .build();
    }
}
