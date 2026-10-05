package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Lifecycle step 5: the fallback when the completion event never arrives. Runs at most twice per
 * attempt (6 hours, then 48 hours after the request) and only acts while the batch is still in
 * {@code EGRESS_REQUESTED}. Talks to one external system, the egress status endpoint. The body
 * arrives with the selection and export subtask.
 */
@ApplicationScoped
public class DeadlineCheckJob {

    /** Job name used in ids and logs. */
    public static final String NAME = "deadline-check";

    private final BatchJobSupport support;

    public DeadlineCheckJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs one check.
     *
     * @param exportBatchId the batch, expected in {@code EGRESS_REQUESTED}
     * @param attempt the attempt the check belongs to
     * @param check 1 for the deadline, 2 for the abandon check
     */
    public void run(String exportBatchId, int attempt, int check) {
        if (support.loadExpecting(NAME, exportBatchId, BatchState.EGRESS_REQUESTED)
                .isEmpty()) {
            return;
        }
        throw new UnsupportedOperationException(
                "deadline-check job body is not built yet (attempt " + attempt + ", check " + check + ")");
    }
}
