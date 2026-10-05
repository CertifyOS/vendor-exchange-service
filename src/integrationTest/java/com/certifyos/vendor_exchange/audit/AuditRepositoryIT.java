package com.certifyos.vendor_exchange.audit;

import com.certifyos.vendor_exchange.ApiTestProfile;
import com.certifyos.vendor_exchange.MongoResource;
import com.certifyos.vendor_exchange.export.batch.BatchState;
import com.certifyos.vendor_exchange.export.batch.ExportBatch;
import com.certifyos.vendor_exchange.export.batch.ExportBatchRepository;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria.Clause;
import com.certifyos.vendor_exchange.export.schedule.SelectionCriteria.Operator;
import com.certifyos.vendor_exchange.persistence.Transactions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The three commit rules: with the transition, standalone, and standalone idempotent on a message id. */
@QuarkusTest
@QuarkusTestResource(MongoResource.class)
@TestProfile(ApiTestProfile.class)
public class AuditRepositoryIT {

    static final Instant NOW = Instant.parse("2026-10-01T06:00:12Z");
    static final SelectionCriteria CRITERIA =
            new SelectionCriteria(List.of(new Clause("data.delegationStatus", Operator.IN, List.of("Direct"))));

    @Inject
    AuditRepository audit;

    @Inject
    ExportBatchRepository batches;

    @Inject
    Transactions transactions;

    private ExportBatch insertBatch(String tenantId) {
        ExportBatch batch = ExportBatch.scheduled(tenantId, "candor", YearMonth.of(2026, 10), 1, CRITERIA, NOW);
        transactions.run(session -> {
            batches.insert(session, batch);
            audit.write(
                    session,
                    AuditEvent.forBatch(AuditEventType.EXPORT_BATCH_SCHEDULED, tenantId, "candor", batch.id(), 1)
                            .occurredAt(NOW)
                            .detail("trigger", "TICK")
                            .detail("period", "2026-10")
                            .detail("seq", 1)
                            .detail("cadence", "monthly")
                            .detail("nextDueAt", NOW)
                            .detail("scheduleVersion", 1L)
                            .build());
            return null;
        });
        return batch;
    }

    @Test
    void eventCommitsWithItsTransition() {
        ExportBatch batch = insertBatch("aud-commit");
        transactions.run(session -> {
            boolean moved = batches.transition(
                    session, batch.id(), BatchState.SCHEDULED, BatchState.NPIS_SELECTED, NOW.plusSeconds(30), null);
            Assertions.assertTrue(moved);
            audit.write(
                    session,
                    AuditEvent.forBatch(
                                    AuditEventType.EXPORT_SELECTION_COMPLETED, "aud-commit", "candor", batch.id(), 1)
                            .occurredAt(NOW.plusSeconds(30))
                            .detail("practitionersSelected", 1240)
                            .detail("criteria", "c")
                            .detail("pages", 13)
                            .detail("durationMs", 1)
                            .build());
            return null;
        });

        List<AuditEvent> trail = audit.findForBatch(batch.id(), 10);
        Assertions.assertEquals(
                List.of(AuditEventType.EXPORT_BATCH_SCHEDULED, AuditEventType.EXPORT_SELECTION_COMPLETED),
                trail.stream().map(AuditEvent::type).toList());
        Assertions.assertEquals(1240, trail.get(1).detail().get("practitionersSelected"));
    }

    @Test
    void eventRollsBackWithItsTransition() {
        ExportBatch batch = insertBatch("aud-rollback");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> transactions.run(session -> {
                    batches.transition(
                            session, batch.id(), BatchState.SCHEDULED, BatchState.EMPTY, NOW.plusSeconds(30), null);
                    audit.write(
                            session,
                            AuditEvent.forBatch(
                                            AuditEventType.EXPORT_BATCH_EMPTY, "aud-rollback", "candor", batch.id(), 1)
                                    .occurredAt(NOW.plusSeconds(30))
                                    .detail("criteria", "c")
                                    .build());
                    throw new IllegalStateException("crash before commit");
                }));

        Assertions.assertEquals(
                BatchState.SCHEDULED, batches.find(batch.id()).orElseThrow().state(), "state rolled back");
        List<AuditEventType> types = audit.findForBatch(batch.id(), 10).stream()
                .map(AuditEvent::type)
                .toList();
        Assertions.assertEquals(List.of(AuditEventType.EXPORT_BATCH_SCHEDULED), types, "event rolled back with it");
    }

    @Test
    void receivedEventIsIdempotentOnMessageIdOnly() {
        ExportBatch batch = insertBatch("aud-idem");
        AuditEvent first = received(batch, "msg-1", NOW.plusSeconds(10));
        AuditEvent duplicate = received(batch, "msg-1", NOW.plusSeconds(11));
        AuditEvent other = received(batch, "msg-2", NOW.plusSeconds(12));

        Assertions.assertTrue(audit.writeIdempotent(first));
        Assertions.assertFalse(audit.writeIdempotent(duplicate), "same messageId is recorded once");
        Assertions.assertTrue(audit.writeIdempotent(other));

        // The partial index covers EXPORT_EVENT_RECEIVED only: another type may carry the same messageId.
        audit.write(AuditEvent.forBatch(AuditEventType.EXPORT_EVENT_MISSED, "aud-idem", "candor", batch.id(), 1)
                .occurredAt(NOW.plusSeconds(13))
                .detail("messageId", "msg-1")
                .detail("check", 1)
                .detail("phaseFound", "COMPLETED")
                .detail("hoursSinceRequest", 6)
                .build());

        List<AuditEvent> trail = audit.findForBatch(batch.id(), 10);
        Assertions.assertEquals(
                List.of(
                        AuditEventType.EXPORT_BATCH_SCHEDULED,
                        AuditEventType.EXPORT_EVENT_RECEIVED,
                        AuditEventType.EXPORT_EVENT_RECEIVED,
                        AuditEventType.EXPORT_EVENT_MISSED),
                trail.stream().map(AuditEvent::type).toList());
        Assertions.assertEquals("msg-1", trail.get(1).detail().get("messageId"));
        Assertions.assertEquals("msg-2", trail.get(2).detail().get("messageId"));
    }

    private static AuditEvent received(ExportBatch batch, String messageId, Instant when) {
        return AuditEvent.forBatch(AuditEventType.EXPORT_EVENT_RECEIVED, batch.tenantId(), "candor", batch.id(), 1)
                .actor(Actors.EGRESS_EVENT)
                .occurredAt(when)
                .detail("messageId", messageId)
                .detail("phase", "COMPLETED")
                .detail("publishTime", when.toString())
                .detail("outputUri", null)
                .detail("totalRecords", null)
                .detail("totalRows", null)
                .build();
    }

    @Test
    void scheduleTrailIsNewestFirstAndSpansBatches() {
        ExportBatch batch = insertBatch("aud-sched");
        audit.write(AuditEvent.of(AuditEventType.SCHEDULE_CREATED, "aud-sched", "candor")
                .actor("user:ops-1")
                .occurredAt(NOW.minusSeconds(600))
                .detail("cadence", "monthly")
                .detail("timezone", "UTC")
                .detail("selection", "s")
                .detail("egressTemplateId", "tpl")
                .detail("nextDueAt", NOW)
                .build());

        List<AuditEvent> trail = audit.findForSchedule("aud-sched", "candor", 10);
        Assertions.assertEquals(
                List.of(AuditEventType.EXPORT_BATCH_SCHEDULED, AuditEventType.SCHEDULE_CREATED),
                trail.stream().map(AuditEvent::type).toList());
        Assertions.assertEquals(batch.id(), trail.get(0).exportBatchId());
        Assertions.assertNull(trail.get(1).exportBatchId());
        Assertions.assertEquals("user:ops-1", trail.get(1).actor());
    }
}
