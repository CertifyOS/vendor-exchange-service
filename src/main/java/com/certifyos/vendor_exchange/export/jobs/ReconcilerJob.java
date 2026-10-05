package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import com.certifyos.vendor_exchange.export.batch.BatchState;
import com.certifyos.vendor_exchange.export.batch.ExportBatch;
import com.certifyos.vendor_exchange.export.batch.ExportBatchRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Lifecycle step 7, the hourly reconciler: a batch row written but never enqueued (a crash between
 * the two writes) sits in {@code SCHEDULED}, {@code NPIS_SELECTED} or {@code EGRESS_COMPLETED} with
 * no job. This job finds rows untouched for longer than {@code reconcilerStaleMinutes} and
 * re-enqueues the job for their state. Because every job checks the row state first, re-enqueueing
 * a job that is already running does nothing. This step wires the recurring job and the query; the
 * re-enqueue and the alert arrive with the observability subtask.
 */
@ApplicationScoped
public class ReconcilerJob {

    /** Recurring job id; registration is idempotent on it. */
    public static final String RECURRING_ID = "vendor-export-reconciler";

    /** Every hour on the hour. */
    public static final String CRON = "0 * * * *";

    /** The most stale rows one run looks at. */
    static final int STALE_LIMIT = 500;

    private static final Logger LOG = Logger.getLogger(ReconcilerJob.class);

    private final VendorExchangeConfig cfg;
    private final ExportBatchRepository batches;
    private final Clock clock;

    public ReconcilerJob(VendorExchangeConfig cfg, ExportBatchRepository batches, Clock clock) {
        this.cfg = cfg;
        this.batches = batches;
        this.clock = clock;
    }

    /**
     * One pass.
     *
     * @return the stale batches found
     */
    public List<ExportBatch> run() {
        if (!cfg.enabled()) {
            LOG.info("reconciler: service disabled, exiting as a no-op");
            return List.of();
        }
        Instant threshold = clock.instant().minus(Duration.ofMinutes(cfg.reconcilerStaleMinutes()));
        List<ExportBatch> stale = batches.findStale(BatchState.reconcilable(), threshold, STALE_LIMIT);
        for (ExportBatch batch : stale) {
            LOG.warnf("reconciler: %s stuck in %s since %s", batch.id(), batch.state(), batch.updatedAt());
        }
        return stale;
    }
}
