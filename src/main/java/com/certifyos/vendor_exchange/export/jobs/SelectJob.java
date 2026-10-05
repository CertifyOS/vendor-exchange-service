package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Lifecycle step 2: page api-layer for the practitioners the batch's criteria select, register
 * them, move the batch to {@code NPIS_SELECTED}. Talks to one external system, api-layer. The body
 * arrives with the selection and export subtask; the precheck and the state contract are here.
 */
@ApplicationScoped
public class SelectJob implements JobRequestHandler<SelectJobRequest> {

    /** Job name used in ids and logs. */
    public static final String NAME = "select";

    private final BatchJobSupport support;

    public SelectJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs the job for one batch.
     *
     * @param request the batch, expected in {@code SCHEDULED}, and the attempt
     */
    @Override
    @Job(name = "select %0")
    public void run(SelectJobRequest request) {
        try (JobLogContext ignored = JobLogContext.open(request.exportBatchId(), jobContext())) {
            if (support.loadExpecting(NAME, request.exportBatchId(), BatchState.SCHEDULED)
                    .isEmpty()) {
                return;
            }
            throw new UnsupportedOperationException("select job body is not built yet");
        }
    }
}
