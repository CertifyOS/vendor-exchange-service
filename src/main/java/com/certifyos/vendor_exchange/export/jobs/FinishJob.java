package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.export.batch.BatchState;
import jakarta.enterprise.context.ApplicationScoped;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Lifecycle step 6: confirm the object egress placed, reconcile the counts, move the batch to
 * {@code DELIVERED}. Talks to GCS and our registry. The body arrives with the selection and export
 * subtask.
 */
@ApplicationScoped
public class FinishJob implements JobRequestHandler<FinishJobRequest> {

    /** Job name used in ids and logs. */
    public static final String NAME = "finish";

    private final BatchJobSupport support;

    public FinishJob(BatchJobSupport support) {
        this.support = support;
    }

    /**
     * Runs the job for one batch.
     *
     * @param request the batch, expected in {@code EGRESS_COMPLETED}, and the attempt
     */
    @Override
    @Job(name = "finish %0")
    public void run(FinishJobRequest request) {
        try (JobLogContext ignored = JobLogContext.open(request.exportBatchId(), JobLogContext.currentJob())) {
            if (support.loadExpecting(NAME, request.exportBatchId(), BatchState.EGRESS_COMPLETED)
                    .isEmpty()) {
                return;
            }
            throw new UnsupportedOperationException("finish job body is not built yet");
        }
    }
}
