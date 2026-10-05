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
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.conversions.Bson;
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
    private final ExportNpiRepository npis;
    private final ScheduleRepository schedules;
    private final AuditRepository audit;
    private final Transactions transactions;
    private final JobEnqueuer enqueuer;
    private final VendorExchangeMetrics metrics;

    public BatchLifecycle(
            ExportBatchRepository batches,
            ExportNpiRepository npis,
            ScheduleRepository schedules,
            AuditRepository audit,
            Transactions transactions,
            JobEnqueuer enqueuer,
            VendorExchangeMetrics metrics) {
        this.batches = batches;
        this.npis = npis;
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

    /**
     * One api-layer page registered: the rows upserted (a repeated page inserts nothing new), the
     * page cursor saved on the batch so a retry resumes after it, and {@code NPIS_REGISTERED} when
     * the page had rows, in one transaction. The batch must still be {@code SCHEDULED}.
     *
     * @param batch the batch
     * @param page the page just fetched
     * @param rows the practitioners on it with a valid NPI
     * @param skipped practitioners on the page without a usable NPI
     * @param now the time of the write
     * @return how many rows were new
     */
    public long registerPage(ExportBatch batch, int page, List<ExportNpi> rows, int skipped, Instant now) {
        long inserted = transactions.run(session -> {
            long fresh = npis.upsertAll(session, rows);
            boolean still = batches.updateInState(
                    session, batch.id(), BatchState.SCHEDULED, now, Updates.set("selection.page", page));
            if (!still) {
                throw new IllegalStateException(batch.id() + " left SCHEDULED during selection");
            }
            if (!rows.isEmpty()) {
                audit.write(
                        session,
                        AuditEvent.forBatch(
                                        AuditEventType.NPIS_REGISTERED,
                                        batch.tenantId(),
                                        batch.vendor(),
                                        batch.id(),
                                        batch.attempt())
                                .occurredAt(now)
                                .detail("page", page)
                                .detail("count", rows.size())
                                .detail("inserted", fresh)
                                .detail("skippedNoNpi", skipped)
                                .detail("firstNpi", rows.get(0).npi())
                                .detail("lastNpi", rows.get(rows.size() - 1).npi())
                                .build());
            }
            return fresh;
        });
        metrics.selectionPage(batch.tenantId());
        return inserted;
    }

    /**
     * Selection finished with practitioners: {@code SCHEDULED} to {@code NPIS_SELECTED} with the
     * counts and {@code EXPORT_SELECTION_COMPLETED}, then the egress request job.
     *
     * @param batch the batch
     * @param pages pages fetched over all attempts
     * @param practitioners rows in the registry
     * @param durationMs this attempt's wall time
     * @param now the time of the write
     * @return the egress request job id
     */
    public UUID completeSelection(ExportBatch batch, int pages, long practitioners, long durationMs, Instant now) {
        Bson updates = Updates.combine(
                Updates.set("selection.practitionersSelected", practitioners),
                Updates.set("selection.completedAt", now),
                Updates.unset("selection.page"));
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_SELECTION_COMPLETED,
                        batch.tenantId(),
                        batch.vendor(),
                        batch.id(),
                        batch.attempt())
                .occurredAt(now)
                .detail("criteria", batch.selection().criteria().toDocuments())
                .detail("pages", pages)
                .detail("practitionersSelected", practitioners)
                .detail("durationMs", durationMs)
                .build();
        transition(batch, BatchState.NPIS_SELECTED, updates, event, now);
        UUID jobId = enqueuer.requestEgress(batch.id(), batch.attempt());
        metrics.selectionPractitioners(batch.tenantId(), batch.vendor(), practitioners);
        LOG.infof(
                "%s selected %d practitioners over %d pages, egress request job %s",
                batch.id(), practitioners, pages, jobId);
        return jobId;
    }

    /**
     * Selection finished with nobody: {@code SCHEDULED} to {@code EMPTY} with
     * {@code EXPORT_BATCH_EMPTY}. No job follows; the period is done.
     *
     * @param batch the batch
     * @param now the time of the write
     */
    public void markEmpty(ExportBatch batch, Instant now) {
        Bson updates = Updates.combine(
                Updates.set("selection.practitionersSelected", 0L),
                Updates.set("selection.completedAt", now),
                Updates.unset("selection.page"));
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_BATCH_EMPTY,
                        batch.tenantId(),
                        batch.vendor(),
                        batch.id(),
                        batch.attempt())
                .occurredAt(now)
                .detail("criteria", batch.selection().criteria().toDocuments())
                .build();
        transition(batch, BatchState.EMPTY, updates, event, now);
        metrics.selectionPractitioners(batch.tenantId(), batch.vendor(), 0);
        LOG.infof("%s selected nobody; EMPTY", batch.id());
    }

    private void transition(ExportBatch batch, BatchState to, Bson updates, AuditEvent event, Instant now) {
        boolean moved = transactions.run(session -> {
            boolean ok = batches.transition(session, batch.id(), batch.state(), to, now, updates);
            if (ok) {
                audit.write(session, event);
            }
            return ok;
        });
        if (!moved) {
            throw new IllegalStateException(batch.id() + " is no longer " + batch.state() + "; " + to + " not written");
        }
    }
}
