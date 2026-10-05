package com.certifyos.vendor_exchange.export.api;

import com.certifyos.vendor_exchange.audit.AuditEvent;
import java.time.Instant;
import java.util.Map;

/** The audit envelope as the API shows it: the stored document, {@code jobId} included (it links to the JobRunr entry). */
public final class AuditViews {

    private AuditViews() {}

    /** One audit event. */
    public record EventView(
            String id,
            String type,
            String tenantId,
            String vendor,
            String exportBatchId,
            Integer attempt,
            String actor,
            String jobId,
            Instant occurredAt,
            Map<String, Object> detail) {

        /** Builds the view. */
        public static EventView of(AuditEvent event) {
            return new EventView(
                    event.id(),
                    event.type().name(),
                    event.tenantId(),
                    event.vendor(),
                    event.exportBatchId(),
                    event.attempt(),
                    event.actor(),
                    event.jobId(),
                    event.occurredAt(),
                    event.detail());
        }
    }
}
