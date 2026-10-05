package com.certifyos.vendor_exchange.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    static final Instant WHEN = Instant.parse("2026-10-01T07:12:09Z");

    @Test
    void catalogueHasTheDesignsTwentyFourTypes() {
        Assertions.assertEquals(24, AuditEventType.values().length);
        Assertions.assertEquals(AuditEventType.Scope.SCHEDULE, AuditEventType.SCHEDULE_CREATED.scope());
        Assertions.assertEquals(AuditEventType.Scope.BATCH, AuditEventType.EXPORT_BATCH_DELIVERED.scope());
        Assertions.assertEquals(AuditEventType.Scope.SERVICE, AuditEventType.EXPORT_TICK_COMPLETED.scope());
        Assertions.assertEquals(AuditEventType.Scope.SERVICE, AuditEventType.EXPORT_EVENT_REJECTED.scope());
    }

    @Test
    void batchEventCarriesTheEnvelopeAndRoundTrips() {
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_BATCH_DELIVERED, "org-xyz", "candor", "org-xyz-candor-2026-10-001", 1)
                .jobId("3f9a")
                .occurredAt(WHEN)
                .detail("fileName", "org-xyz_org-xyz-candor-2026-10-001_20261001.csv")
                .detail("rowCount", 1873)
                .detail("deliveredAt", "2026-10-01T07:12:09Z")
                .detail("bytes", 1912044)
                .detail("completionSource", "EVENT")
                .detail("md5", "x")
                .detail("absent", null)
                .build();

        Assertions.assertTrue(event.id().startsWith("ev-"));
        Assertions.assertEquals(Actors.SYSTEM, event.actor(), "system actor by default");
        Assertions.assertEquals(1, event.attempt());
        Assertions.assertFalse(event.detail().containsKey("absent"), "null detail values are left out");

        Document doc = event.toDocument();
        Assertions.assertFalse(doc.containsKey("_id"), "the driver assigns _id");
        Assertions.assertEquals("EXPORT_BATCH_DELIVERED", doc.getString("type"));
        Assertions.assertEquals(1873, doc.get("detail", Document.class).getInteger("rowCount"));
        Assertions.assertEquals(event, AuditEvent.fromDocument(doc));
    }

    @Test
    void scheduleEventHasNoBatchAndServiceEventNeedsNoTenant() {
        AuditEvent schedule = AuditEvent.of(AuditEventType.SCHEDULE_DISABLED, "org-xyz", "candor")
                .actor("user:ops-1")
                .occurredAt(WHEN)
                .detail("reason", "hold")
                .detail("disabledAt", "2026-10-01T00:00:00Z")
                .build();
        Assertions.assertNull(schedule.exportBatchId());
        Assertions.assertFalse(schedule.toDocument().containsKey("exportBatchId"));

        AuditEvent tick = AuditEvent.of(AuditEventType.EXPORT_TICK_COMPLETED, null, null)
                .occurredAt(WHEN)
                .detail("schedulesDue", 0)
                .detail("batchesCreated", java.util.List.of())
                .detail("skippedAlreadyExists", 0)
                .detail("durationMs", 1)
                .build();
        Assertions.assertNull(tick.tenantId());
        Assertions.assertEquals(tick, AuditEvent.fromDocument(tick.toDocument()));
    }

    @Test
    void envelopeRulesAreEnforced() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> AuditEvent.of(AuditEventType.EXPORT_BATCH_FAILED, "org-xyz", "candor")
                        .build(),
                "a batch event without a batch id");
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> AuditEvent.of(AuditEventType.SCHEDULE_CREATED, null, "candor")
                        .build(),
                "a schedule event without a tenant");
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> AuditEvent.of(AuditEventType.SCHEDULE_CREATED, "org-xyz", "candor")
                        .batch("org-xyz-candor-2026-10-001", 1)
                        .build(),
                "a schedule event with a batch id");
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> AuditEvent.of(AuditEventType.SCHEDULE_CREATED, "org-xyz", "candor")
                        .actor(" ")
                        .build(),
                "a blank actor");
    }

    @Test
    void detailIsCopiedAndImmutable() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("count", 1);
        AuditEvent event = AuditEvent.of(AuditEventType.SCHEDULE_CREATED, "org-xyz", "candor")
                .details(detail)
                .detail("cadence", "monthly")
                .detail("timezone", "UTC")
                .detail("selection", "s")
                .detail("egressTemplateId", "tpl")
                .detail("nextDueAt", "n")
                .build();
        detail.put("count", 2);
        Assertions.assertEquals(1, event.detail().get("count"));
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> event.detail().put("x", 1));
    }
}
