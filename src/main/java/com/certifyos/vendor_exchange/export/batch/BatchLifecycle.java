package com.certifyos.vendor_exchange.export.batch;

import com.certifyos.vendor_exchange.audit.AuditEvent;
import com.certifyos.vendor_exchange.audit.AuditEventType;
import com.certifyos.vendor_exchange.audit.AuditRepository;
import com.certifyos.vendor_exchange.export.jobs.JobEnqueuer;
import com.certifyos.vendor_exchange.export.jobs.JobIds;
import com.certifyos.vendor_exchange.export.jobs.SelectJob;
import com.certifyos.vendor_exchange.export.schedule.Schedule;
import com.certifyos.vendor_exchange.export.schedule.ScheduleRepository;
import com.certifyos.vendor_exchange.metrics.VendorExchangeMetrics;
import com.certifyos.vendor_exchange.persistence.AlreadyExistsException;
import com.certifyos.vendor_exchange.persistence.Transactions;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * The batch transitions more than one caller performs, each one transaction with its audit event,
 * so the tick, run-now and supersede write the same facts the same way. Jobs are enqueued after
 * the transaction commits, never inside it.
 */
@ApplicationScoped
public class BatchLifecycle {

    /** Who asked for a batch, as {@code EXPORT_BATCH_SCHEDULED} records it. */
    public enum Trigger {
        TICK,
        MANUAL,
        SUPERSEDE
    }

    private static final Logger LOG = Logger.getLogger(BatchLifecycle.class);

    private final ExportBatchRepository batches;
    private final ScheduleRepository schedules;
    private final AuditRepository audit;
    private final Transactions transactions;
    private final JobEnqueuer enqueuer;
    private final VendorExchangeMetrics metrics;

    public BatchLifecycle(
            ExportBatchRepository batches,
            ScheduleRepository schedules,
            AuditRepository audit,
            Transactions transactions,
            JobEnqueuer enqueuer,
            VendorExchangeMetrics metrics) {
        this.batches = batches;
        this.schedules = schedules;
        this.audit = audit;
        this.transactions = transactions;
        this.enqueuer = enqueuer;
        this.metrics = metrics;
    }

    /**
     * A batch that was just scheduled.
     *
     * @param batch the inserted row
     * @param jobId its select job
     * @param nextDueAt the schedule's new due instant
     */
    public record Scheduled(ExportBatch batch, UUID jobId, Instant nextDueAt) {}

    /**
     * Creates a batch for a schedule's period: inserts the row in {@code SCHEDULED}, advances the
     * schedule ({@code nextDueAt}, {@code lastBatchId}, {@code lastRunAt}) and writes
     * {@code EXPORT_BATCH_SCHEDULED} in one transaction, then enqueues the select job.
     *
     * @param schedule the schedule as the caller read it; its version is the compare-and-set token
     * @param period the month the batch is for
     * @param seq 1 from the tick and run-now; supersede passes the next
     * @param trigger who asked
     * @param actor the operator or the system
     * @param now the time of the write
     * @param companion another event to commit with the batch (run-now's {@code SCHEDULE_RUN_NOW}),
     *     or null
     * @return the batch, or empty when the period already has this sequence (the unique index
     *     refused, nothing was written)
     * @throws StaleScheduleException when the schedule is no longer at the version read
     */
    public Optional<Scheduled> schedule(
            Schedule schedule,
            YearMonth period,
            int seq,
            Trigger trigger,
            String actor,
            Instant now,
            AuditEvent companion) {
        ExportBatch batch =
                ExportBatch.scheduled(schedule.tenantId(), schedule.vendor(), period, seq, schedule.selection(), now);
        Instant nextDueAt = schedule.cadence().next(now, schedule.timezone());
        UUID jobId = JobIds.of(SelectJob.NAME, batch.id(), batch.attempt());
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_BATCH_SCHEDULED,
                        schedule.tenantId(),
                        schedule.vendor(),
                        batch.id(),
                        batch.attempt())
                .actor(actor)
                .jobId(jobId.toString())
                .occurredAt(now)
                .detail("period", period.toString())
                .detail("seq", seq)
                .detail("cadence", schedule.cadence().toDocument())
                .detail("trigger", trigger.name())
                .detail("nextDueAt", nextDueAt)
                .build();
        try {
            transactions.run(session -> {
                batches.insert(session, batch);
                if (!schedules.advance(session, schedule.id(), schedule.version(), nextDueAt, batch.id(), now)) {
                    throw new StaleScheduleException(schedule.id(), schedule.version());
                }
                audit.write(session, event);
                if (companion != null) {
                    audit.write(session, companion);
                }
                return null;
            });
        } catch (AlreadyExistsException exists) {
            LOG.warnf("%s already exists; %s trigger wrote nothing", batch.id(), trigger);
            return Optional.empty();
        }
        enqueuer.select(batch.id(), batch.attempt());
        metrics.batchCreated(schedule.tenantId(), schedule.vendor());
        LOG.infof("%s scheduled by %s (%s), select job %s, next due %s", batch.id(), actor, trigger, jobId, nextDueAt);
        return Optional.of(new Scheduled(batch, jobId, nextDueAt));
    }
}
