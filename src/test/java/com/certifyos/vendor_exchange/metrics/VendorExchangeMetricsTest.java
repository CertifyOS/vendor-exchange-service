package com.certifyos.vendor_exchange.metrics;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VendorExchangeMetricsTest {

    /** The fourteen names in the design's Observability section. */
    static final List<String> DESIGN_NAMES = List.of(
            VendorExchangeMetrics.TICK_DURATION,
            VendorExchangeMetrics.TICK_SCHEDULES_DUE,
            VendorExchangeMetrics.SCHEDULES_BY_STATE,
            VendorExchangeMetrics.BATCHES_CREATED,
            VendorExchangeMetrics.SELECTION_PRACTITIONERS,
            VendorExchangeMetrics.SELECTION_PAGES,
            VendorExchangeMetrics.EGRESS_WAIT_SECONDS,
            VendorExchangeMetrics.EGRESS_FAILED,
            VendorExchangeMetrics.COMPLETION_SOURCE,
            VendorExchangeMetrics.EVENT_REJECTED,
            VendorExchangeMetrics.RECONCILE_MISMATCH,
            VendorExchangeMetrics.BATCHES_BY_STATE,
            VendorExchangeMetrics.JOBS_RETRIES,
            VendorExchangeMetrics.JOBS_FINAL_FAILURES);

    private SimpleMeterRegistry registry;
    private VendorExchangeMetrics metrics;

    @BeforeEach
    void before() {
        registry = new SimpleMeterRegistry();
        metrics = new VendorExchangeMetrics(registry);
    }

    private Set<String> names() {
        return registry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toSet());
    }

    @Test
    void tickMetersExistBeforeAnyTick() {
        Assertions.assertTrue(names().contains(VendorExchangeMetrics.TICK_DURATION));
        Assertions.assertTrue(names().contains(VendorExchangeMetrics.TICK_SCHEDULES_DUE));
        metrics.tick(3, Duration.ofMillis(120));
        Assertions.assertEquals(
                3.0,
                registry.get(VendorExchangeMetrics.TICK_SCHEDULES_DUE).gauge().value());
        Assertions.assertEquals(
                1, registry.get(VendorExchangeMetrics.TICK_DURATION).timer().count());
    }

    @Test
    void everyDesignNameIsReachable() {
        metrics.batchCreated("org-a", "candor");
        metrics.selectionPractitioners("org-a", "candor", 1240);
        metrics.selectionPage("org-a");
        metrics.egressWaitSeconds("org-a", 712);
        metrics.egressFailed("org-a", "EGRESS_DID_NOT_FINISH");
        metrics.completion("EVENT");
        metrics.eventRejected("UNKNOWN_CORRELATION");
        metrics.reconcileMismatch("org-a");
        metrics.jobRetry("SelectJob");
        metrics.jobFinalFailure("SelectJob");
        metrics.registerGauge(VendorExchangeMetrics.BATCHES_BY_STATE, Tags.of("state", "SCHEDULED"), () -> 2);
        metrics.registerGauge(VendorExchangeMetrics.SCHEDULES_BY_STATE, Tags.of("state", "ENABLED"), () -> 1);
        Set<String> registered = names();
        for (String name : DESIGN_NAMES) {
            Assertions.assertTrue(registered.contains(name), "missing " + name);
        }
        Assertions.assertEquals(DESIGN_NAMES.size(), registered.size(), "nothing beyond the design's names");
    }

    @Test
    void tagsAreTheDesignsAndGaugesKeepTheirHolder() {
        metrics.batchCreated("org-a", "candor");
        metrics.batchCreated("org-a", "candor");
        Assertions.assertEquals(
                2.0,
                registry.get(VendorExchangeMetrics.BATCHES_CREATED)
                        .tags("tenant", "org-a", "vendor", "candor")
                        .counter()
                        .count());
        metrics.selectionPractitioners("org-a", "candor", 10);
        metrics.selectionPractitioners("org-a", "candor", 12);
        Assertions.assertEquals(
                12.0,
                registry.get(VendorExchangeMetrics.SELECTION_PRACTITIONERS)
                        .tags("tenant", "org-a", "vendor", "candor")
                        .gauge()
                        .value());
        Assertions.assertEquals(
                1,
                registry.find(VendorExchangeMetrics.SELECTION_PRACTITIONERS)
                        .gauges()
                        .size(),
                "one gauge per tag set, re-set in place");
    }
}
