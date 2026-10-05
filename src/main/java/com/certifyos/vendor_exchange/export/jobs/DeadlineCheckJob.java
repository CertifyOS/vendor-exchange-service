package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Lifecycle step 5: the fallback when the completion event never arrives. Runs at most twice per
 * attempt (6 hours, then 48 hours after the request) and only acts while the batch is still in
 * {@code EGRESS_REQUESTED}. Talks to one external system, the egress status endpoint. The body
 * arrives with the selection and export subtask.
 */
@ApplicationScoped
public class DeadlineCheckJob implements JobRequestHandler<DeadlineCheckJobRequest> {

    /** Job name used in ids and logs. */
    public static final String NAME = "deadline-check";

    private final BatchJobSupport support;

    public DeadlineCheckJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs one check.
     *
     * @param request the batch, expected in {@code EGRESS_REQUESTED}, the attempt and the check number
     */
    @Override
    @Job(name = "deadline-check %0")
    public void run(DeadlineCheckJobRequest request) {
        try (JobLogContext ignored = JobLogContext.open(request.exportBatchId(), jobContext())) {
            if (support.loadExpecting(NAME, request.exportBatchId(), BatchState.EGRESS_REQUESTED)
                    .isEmpty()) {
                return;
            }
            throw new UnsupportedOperationException("deadline-check job body is not built yet (" + request + ")");
        }
    }
}
