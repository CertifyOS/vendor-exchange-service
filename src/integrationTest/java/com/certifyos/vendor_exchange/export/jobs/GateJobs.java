package com.certifyos.vendor_exchange.export.jobs;

import java.util.concurrent.atomic.AtomicInteger;
import org.jobrunr.jobs.annotations.Job;

/**
 * Static jobs for the JobRunr gate. JobRunr invokes a captured static method by plain reflection,
 * so this class must be public (file-ingestion finding 4).
 */
public final class GateJobs {

    public static final AtomicInteger RUNS = new AtomicInteger();
    public static final AtomicInteger FLAKY = new AtomicInteger();

    private GateJobs() {}

    public static void run() {
        RUNS.incrementAndGet();
    }

    @Job(retries = 2)
    public static void flaky() {
        if (FLAKY.incrementAndGet() < 2) {
            throw new IllegalStateException("first attempt fails on purpose");
        }
    }
}
