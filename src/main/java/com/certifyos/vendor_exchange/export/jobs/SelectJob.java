package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Lifecycle step 2: page api-layer for the practitioners the batch's criteria select, register
 * them, move the batch to {@code NPIS_SELECTED}. Talks to one external system, api-layer. The body
 * arrives with the selection and export subtask; the precheck and the state contract are here.
 */
@ApplicationScoped
public class SelectJob {

    /** Job name used in ids and logs. */
    public static final String NAME = "select";

    private final BatchJobSupport support;

    public SelectJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs the job for one batch.
     *
     * @param exportBatchId the batch, expected in {@code SCHEDULED}
     */
    public void run(String exportBatchId) {
        if (support.loadExpecting(NAME, exportBatchId, BatchState.SCHEDULED).isEmpty()) {
            return;
        }
        throw new UnsupportedOperationException("select job body is not built yet");
    }
}
