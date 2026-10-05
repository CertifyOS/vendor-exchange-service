package com.certifyos.vendor_exchange.export.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import org.jobrunr.scheduling.JobScheduler;

/**
 * The one place jobs are enqueued, with deterministic ids and JobRunr's IoC lambda form, so the job
 * bean is resolved from CDI when the job runs rather than captured as a proxy. Never call these
 * inside {@code Transactions.run}: the body may be retried and an enqueue is not a Mongo write.
 */
@ApplicationScoped
public class JobEnqueuer {

    private final JobScheduler scheduler;

    public JobEnqueuer(JobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Enqueues the select job for a batch attempt and returns its id. */
    public UUID select(String exportBatchId, int attempt) {
        UUID id = JobIds.of(SelectJob.NAME, exportBatchId, attempt);
        scheduler.<SelectJob>enqueue(id, job -> job.run(exportBatchId));
        return id;
    }

    /** Enqueues the egress request job for a batch attempt and returns its id. */
    public UUID requestEgress(String exportBatchId, int attempt) {
        UUID id = JobIds.of(RequestEgressJob.NAME, exportBatchId, attempt);
        scheduler.<RequestEgressJob>enqueue(id, job -> job.run(exportBatchId));
        return id;
    }

    /** Schedules a deadline check at a future instant and returns its id. */
    public UUID deadlineCheck(String exportBatchId, int attempt, int check, Instant at) {
        UUID id = JobIds.deadline(exportBatchId, attempt, check);
        scheduler.<DeadlineCheckJob>schedule(id, at, job -> job.run(exportBatchId, attempt, check));
        return id;
    }

    /** Enqueues the finish job for a batch attempt and returns its id. */
    public UUID finish(String exportBatchId, int attempt) {
        UUID id = JobIds.of(FinishJob.NAME, exportBatchId, attempt);
        scheduler.<FinishJob>enqueue(id, job -> job.run(exportBatchId));
        return id;
    }

    /** Deletes a pending job (a deadline check that is no longer needed); unknown ids are ignored. */
    public void delete(UUID jobId) {
        try {
            scheduler.delete(jobId);
        } catch (org.jobrunr.scheduling.exceptions.JobNotFoundException ignored) {
            // already gone, which is the outcome we wanted
        }
    }
}
