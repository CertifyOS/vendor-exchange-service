package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.ApiTestProfile;
import com.certifyos.vendor_exchange.MongoResource;
import com.certifyos.vendor_exchange.audit.AuditEvent;
import com.certifyos.vendor_exchange.audit.AuditEventType;
import com.certifyos.vendor_exchange.export.batch.BatchState;
import com.certifyos.vendor_exchange.export.batch.ExportBatch;
import com.certifyos.vendor_exchange.export.batch.ExportBatchRepository;
import com.certifyos.vendor_exchange.export.schedule.Cadence;
import com.certifyos.vendor_exchange.export.schedule.Schedule;
import com.certifyos.vendor_exchange.export.schedule.ScheduleRepository;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria.Clause;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria.Operator;
import com.certifyos.vendor_exchange.persistence.Collections;
import com.certifyos.vendor_exchange.persistence.Transactions;
import com.mongodb.client.model.Filters;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The two recurring job bodies, run directly on the api role (the beans exist on both roles). */
@QuarkusTest
@QuarkusTestResource(MongoResource.class)
@TestProfile(ApiTestProfile.class)
public class TickAndReconcilerIT {

    static final SelectionCriteria CRITERIA =
            new SelectionCriteria(List.of(new Clause("data.delegationStatus", Operator.IN, List.of("Direct"))));

    @Inject
    TickJob tick;

    @Inject
    ReconcilerJob reconciler;

    @Inject
    ScheduleRepository schedules;

    @Inject
    ExportBatchRepository batches;

    @Inject
    Collections collections;

    @Inject
    Transactions transactions;

    @Test
    void tickFindsDueSchedulesAndRecordsTheFact() {
        Schedule due = Schedule.create(
                "tick-due",
                "candor",
                Cadence.monthly(1),
                ZoneId.of("UTC"),
                CRITERIA,
                "tpl",
                Instant.now().minusSeconds(60),
                "user:ops",
                Instant.now());
        transactions.run(session -> {
            schedules.insert(session, due);
            return null;
        });

        TickJob.TickResult result = tick.run();

        Assertions.assertTrue(result.tickId().startsWith("tick-"));
        Assertions.assertTrue(result.schedulesDue() >= 1, "the due schedule is counted");
        Document event = collections
                .events()
                .find(Filters.and(
                        Filters.eq("type", AuditEventType.EXPORT_TICK_COMPLETED.name()),
                        Filters.eq("detail.tickId", result.tickId())))
                .first();
        Assertions.assertNotNull(event, "EXPORT_TICK_COMPLETED written as a standalone fact");
        AuditEvent recorded = AuditEvent.fromDocument(event);
        Assertions.assertNull(recorded.tenantId(), "a tick has no tenant");
        Assertions.assertEquals(result.schedulesDue(), recorded.detail().get("schedulesDue"));
    }

    @Test
    void reconcilerFindsStaleRowsInReconcilableStatesOnly() {
        Instant old = Instant.now().minusSeconds(3600);
        ExportBatch stale = ExportBatch.scheduled("recon-stale", "candor", YearMonth.of(2026, 10), 1, CRITERIA, old);
        ExportBatch fresh =
                ExportBatch.scheduled("recon-fresh", "candor", YearMonth.of(2026, 10), 1, CRITERIA, Instant.now());
        ExportBatch waiting =
                ExportBatch.scheduled("recon-waiting", "candor", YearMonth.of(2026, 10), 1, CRITERIA, old);
        transactions.run(session -> {
            batches.insert(session, stale);
            batches.insert(session, fresh);
            batches.insert(session, waiting);
            batches.transition(session, waiting.id(), BatchState.SCHEDULED, BatchState.NPIS_SELECTED, old, null);
            batches.transition(session, waiting.id(), BatchState.NPIS_SELECTED, BatchState.EGRESS_REQUESTED, old, null);
            return null;
        });

        List<String> found = reconciler.run().stream()
                .map(ExportBatch::id)
                .filter(id -> id.startsWith("recon-"))
                .toList();

        Assertions.assertTrue(found.contains(stale.id()), "an old SCHEDULED row is stuck");
        Assertions.assertFalse(found.contains(fresh.id()), "a fresh row is not stale");
        Assertions.assertFalse(found.contains(waiting.id()), "EGRESS_REQUESTED belongs to the deadline check");
    }
}
