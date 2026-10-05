package com.certifyos.vendor_exchange.export.jobs;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.jobrunr.scheduling.JobScheduler;

/**
 * Registers the two recurring jobs in code at startup of the worker role. Registration is idempotent
 * by id: the first boot creates the row in {@code jobrunr_recurring_jobs}, every later boot updates
 * it. No endpoint and no manual step. JobRunr's open-source edition does not run a recurring job
 * that was missed while no job server was alive; the tick's {@code nextDueAt <= now} query makes a
 * missed day cost one day of delay, never a period.
 */
@ApplicationScoped
public class RecurringJobs {

    private static final Logger LOG = Logger.getLogger(RecurringJobs.class);

    private final JobScheduler scheduler;

    public RecurringJobs(JobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    void onStart(@Observes StartupEvent event) {
        if (!ConfigUtils.getProfiles().contains("worker")) {
            return;
        }
        register();
    }

    /** Registers both recurring jobs; public so a test can call it on a role that skips it at boot. */
    public void register() {
        scheduler.<TickJob>scheduleRecurrently(TickJob.RECURRING_ID, TickJob.CRON, job -> job.run());
        scheduler.<ReconcilerJob>scheduleRecurrently(ReconcilerJob.RECURRING_ID, ReconcilerJob.CRON, job -> job.run());
        LOG.infof(
                "recurring jobs registered: %s (%s), %s (%s)",
                TickJob.RECURRING_ID, TickJob.CRON, ReconcilerJob.RECURRING_ID, ReconcilerJob.CRON);
    }
}
