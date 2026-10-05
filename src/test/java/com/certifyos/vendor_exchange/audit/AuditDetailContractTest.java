package com.certifyos.vendor_exchange.audit;

import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuditDetailContractTest {

    @Test
    void everyEventTypeHasAnEntryFromTheDesignTable() {
        for (AuditEventType type : AuditEventType.values()) {
            Assertions.assertFalse(
                    AuditDetailContract.requiredKeys(type).isEmpty(),
                    type + " has no required detail keys; the design's per-event table lists some for every type");
        }
    }

    @Test
    void aWriterThatForgetsARequiredKeyIsRefusedAtBuild() {
        IllegalArgumentException refused =
                Assertions.assertThrows(IllegalArgumentException.class, () -> AuditEvent.forBatch(
                                AuditEventType.EXPORT_BATCH_FAILED, "org-1", "candor", "org-1-candor-2026-10-001", 1)
                        .detail("failedStep", "EGRESS")
                        .detail("cause", "EGRESS_FAILED")
                        .build());
        Assertions.assertTrue(refused.getMessage().contains("EXPORT_BATCH_FAILED"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("lastError"), refused.getMessage());
    }

    @Test
    void aKeyNamedWithANullValueCountsAsNamedAndIsNotStored() {
        // An egress failure event has no output URI; the writer still names the field.
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_EVENT_RECEIVED, "org-1", "candor", "org-1-candor-2026-10-001", 1)
                .detail("messageId", "m-1")
                .detail("publishTime", "2026-10-01T07:12:01Z")
                .detail("phase", "FAILED")
                .detail("outputUri", null)
                .detail("totalRecords", null)
                .detail("totalRows", null)
                .build();
        Assertions.assertFalse(event.detail().containsKey("outputUri"));
        Assertions.assertEquals("FAILED", event.detail().get("phase"));
    }

    @Test
    void extraKeysAreAllowed() {
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.NPIS_REGISTERED, "org-1", "candor", "org-1-candor-2026-10-001", 1)
                .detail("page", 0)
                .detail("count", 2)
                .detail("firstNpi", "1234567893")
                .detail("lastNpi", "2345678918")
                .detail("inserted", 2L)
                .detail("skippedNoNpi", 0)
                .build();
        Assertions.assertEquals(2L, event.detail().get("inserted"));
        Assertions.assertEquals(
                Set.of("page", "count", "firstNpi", "lastNpi"),
                AuditDetailContract.requiredKeys(AuditEventType.NPIS_REGISTERED));
    }

    @Test
    void theAuditLineCarriesIdsAndAlertKeysOnly() {
        AuditEvent event = AuditEvent.forBatch(
                        AuditEventType.EXPORT_BATCH_FAILED, "org-1", "candor", "org-1-candor-2026-10-001", 2)
                .actor("ops@certifyos.com")
                .detail("failedStep", "EGRESS")
                .detail("cause", "FILE_NOT_FOUND")
                .detail("lastError", "no object at gs://b/from/org-1/f.csv")
                .build();
        String line = AuditRepository.line(event);
        Assertions.assertEquals(
                "AUDIT type=EXPORT_BATCH_FAILED tenantId=org-1 vendor=candor exportBatchId=org-1-candor-2026-10-001 attempt=2 "
                        + "actor=ops@certifyos.com cause=FILE_NOT_FOUND",
                line);
        Assertions.assertFalse(line.contains("lastError"), "free text never reaches the line");
    }
}
