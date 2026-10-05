package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Lifecycle step 3: cancel a prior attempt on retry, pin the template, ask egress to build and
 * place the file, schedule the deadline check, move the batch to {@code EGRESS_REQUESTED}. Talks to
 * one external system, egress. The body arrives with the selection and export subtask.
 */
@ApplicationScoped
public class RequestEgressJob implements JobRequestHandler<RequestEgressJobRequest> {

    /** Job name used in ids and logs. */
    public static final String NAME = "request-egress";

    private final BatchJobSupport support;

    public RequestEgressJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs the job for one batch.
     *
     * @param request the batch, expected in {@code NPIS_SELECTED}, and the attempt
     */
    @Override
    @Job(name = "request-egress %0")
    public void run(RequestEgressJobRequest request) {
        String exportBatchId = request.exportBatchId();
        if (support.loadExpecting(NAME, exportBatchId, BatchState.NPIS_SELECTED).isEmpty()) {
            return;
        }
        throw new UnsupportedOperationException("request-egress job body is not built yet");
    }
}
