package com.certifyos.vendor_exchange.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The metrics from the design's Observability section, one method per name. Tags carry tenant,
 * vendor, state, reason, source or job, never a practitioner. Telemetry is best effort: nothing
 * here throws into a caller's transaction. Names use dots; the Prometheus endpoint renders them
 * with underscores ({@code vendor_export_tick_duration_seconds}).
 */
@ApplicationScoped
public class VendorExchangeMetrics {

    /** Every metric name in the design, for the endpoint test and the runbook. */
    public static final String TICK_DURATION = "vendor_export.tick.duration";

    public static final String TICK_SCHEDULES_DUE = "vendor_export.tick.schedules_due";
    public static final String SCHEDULES_BY_STATE = "vendor_export.schedules.by_state";
    public static final String BATCHES_CREATED = "vendor_export.batches.created";
    public static final String SELECTION_PRACTITIONERS = "vendor_export.selection.practitioners";
    public static final String SELECTION_PAGES = "vendor_export.selection.pages";
    public static final String EGRESS_WAIT_SECONDS = "vendor_export.egress.wait.seconds";
    public static final String EGRESS_FAILED = "vendor_export.egress.failed";
    public static final String COMPLETION_SOURCE = "vendor_export.completion.source";
    public static final String EVENT_REJECTED = "vendor_export.event.rejected";
    public static final String RECONCILE_MISMATCH = "vendor_export.reconcile.mismatch";
    public static final String BATCHES_BY_STATE = "vendor_export.batches.by_state";
    public static final String JOBS_RETRIES = "vendor_export.jobs.retries";
    public static final String JOBS_FINAL_FAILURES = "vendor_export.jobs.final_failures";

    private final MeterRegistry registry;
    private final Timer tickDuration;
    private final AtomicLong tickSchedulesDue = new AtomicLong();
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    public VendorExchangeMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.tickDuration = Timer.builder(TICK_DURATION)
                .description("Wall time of one schedule tick")
                .register(registry);
        Gauge.builder(TICK_SCHEDULES_DUE, tickSchedulesDue, AtomicLong::doubleValue)
                .description("Schedules the last tick found due")
                .register(registry);
    }

    /** Records one tick. */
    public void tick(int schedulesDue, Duration duration) {
        tickSchedulesDue.set(schedulesDue);
        tickDuration.record(duration);
    }

    /** A batch was created by the tick or run-now. */
    public void batchCreated(String tenant, String vendor) {
        counter(BATCHES_CREATED, Tags.of("tenant", tenant, "vendor", vendor)).increment();
    }

    /** Practitioners the last selection registered for the tenant and vendor. */
    public void selectionPractitioners(String tenant, String vendor, long count) {
        gauge(SELECTION_PRACTITIONERS, Tags.of("tenant", tenant, "vendor", vendor))
                .set(count);
    }

    /** One api-layer page fetched. */
    public void selectionPage(String tenant) {
        counter(SELECTION_PAGES, Tags.of("tenant", tenant)).increment();
    }

    /** Seconds between the egress request and its completion, for the tenant's last batch. */
    public void egressWaitSeconds(String tenant, long seconds) {
        gauge(EGRESS_WAIT_SECONDS, Tags.of("tenant", tenant)).set(seconds);
    }

    /** An egress attempt failed for a reason ({@code EGRESS_DID_NOT_FINISH}, {@code FILE_NOT_FOUND}, ...). */
    public void egressFailed(String tenant, String reason) {
        counter(EGRESS_FAILED, Tags.of("tenant", tenant, "reason", reason)).increment();
    }

    /** How a completion was learned: {@code EVENT} or {@code DEADLINE}. */
    public void completion(String source) {
        counter(COMPLETION_SOURCE, Tags.of("source", source)).increment();
    }

    /** A push event was rejected for a reason. */
    public void eventRejected(String reason) {
        counter(EVENT_REJECTED, Tags.of("reason", reason)).increment();
    }

    /** Egress's counts disagreed with the registry. */
    public void reconcileMismatch(String tenant) {
        counter(RECONCILE_MISMATCH, Tags.of("tenant", tenant)).increment();
    }

    /** A job attempt failed and will be retried. */
    public void jobRetry(String job) {
        counter(JOBS_RETRIES, Tags.of("job", job)).increment();
    }

    /** A job exhausted its retries. */
    public void jobFinalFailure(String job) {
        counter(JOBS_FINAL_FAILURES, Tags.of("job", job)).increment();
    }

    /**
     * Registers a gauge whose value is read on every scrape. Used by the export lane for the
     * by-state gauges, whose values come from the database.
     *
     * @param name the metric name
     * @param tags its tags
     * @param value the supplier, read on scrape
     */
    public void registerGauge(String name, Tags tags, Supplier<Number> value) {
        Gauge.builder(name, value::get).tags(tags).register(registry);
    }

    private Counter counter(String name, Tags tags) {
        return registry.counter(name, tags);
    }

    private AtomicLong gauge(String name, Tags tags) {
        return gaugeValues.computeIfAbsent(name + tags, key -> registry.gauge(name, tags, new AtomicLong()));
    }
}
