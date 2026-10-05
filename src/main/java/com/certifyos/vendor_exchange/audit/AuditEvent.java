package com.certifyos.vendor_exchange.audit;

import com.certifyos.vendor_exchange.persistence.Documents;
import com.certifyos.vendor_exchange.persistence.Ids;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;

/**
 * The common audit envelope from the design. Append-only; written in the same transaction as the
 * state change where one exists, otherwise as a standalone fact. {@code detail} is the per-type
 * payload the design's table lists; it carries ids and counts, never field values or PII.
 *
 * @param id {@code ev-<uuid v7>}
 * @param type the event type
 * @param tenantId the tenant; null only for {@link AuditEventType.Scope#SERVICE} events
 * @param vendor the vendor; null only for service events
 * @param exportBatchId the batch; required for batch events, null for schedule events
 * @param attempt the batch attempt the event belongs to, or null
 * @param actor who caused it: {@link Actors#SYSTEM}, {@link Actors#EGRESS_EVENT} or an operator id
 * @param jobId the JobRunr job that wrote it, or null for HTTP handlers
 * @param occurredAt when it happened
 * @param detail the per-type payload, immutable
 */
public record AuditEvent(
        String id,
        AuditEventType type,
        String tenantId,
        String vendor,
        String exportBatchId,
        Integer attempt,
        String actor,
        String jobId,
        Instant occurredAt,
        Map<String, Object> detail) {

    public AuditEvent {
        requireActor(type, actor);
        requireIds(type, tenantId, vendor, exportBatchId);
        detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    private static void requireActor(AuditEventType type, String actor) {
        if (type == null) {
            throw new IllegalArgumentException("audit event type is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("audit event actor is required for " + type);
        }
    }

    private static void requireIds(AuditEventType type, String tenantId, String vendor, String exportBatchId) {
        if (type.requiresTenant() && (tenantId == null || vendor == null)) {
            throw new IllegalArgumentException(type + " requires tenantId and vendor");
        }
        if (type.requiresBatch() && exportBatchId == null) {
            throw new IllegalArgumentException(type + " requires exportBatchId");
        }
        if (type.scope() == AuditEventType.Scope.SCHEDULE && exportBatchId != null) {
            throw new IllegalArgumentException(type + " is a schedule event and carries no exportBatchId");
        }
    }

    /**
     * Starts an event for a schedule or the service.
     *
     * @param type the type
     * @param tenantId the tenant, or null for a service event
     * @param vendor the vendor, or null for a service event
     * @return a builder with the system actor and the current time
     */
    public static Builder of(AuditEventType type, String tenantId, String vendor) {
        return new Builder(type, tenantId, vendor);
    }

    /**
     * Starts an event for a batch.
     *
     * @param type the type
     * @param tenantId the tenant
     * @param vendor the vendor
     * @param exportBatchId the batch
     * @param attempt the attempt
     * @return a builder with the system actor and the current time
     */
    public static Builder forBatch(
            AuditEventType type, String tenantId, String vendor, String exportBatchId, int attempt) {
        return new Builder(type, tenantId, vendor).batch(exportBatchId, attempt);
    }

    /** The BSON document; the driver adds its own {@code _id}. */
    public Document toDocument() {
        Document doc = new Document("id", id)
                .append("type", type.name())
                .append("actor", actor)
                .append("occurredAt", occurredAt);
        Documents.put(doc, "tenantId", tenantId);
        Documents.put(doc, "vendor", vendor);
        Documents.put(doc, "exportBatchId", exportBatchId);
        Documents.put(doc, "attempt", attempt);
        Documents.put(doc, "jobId", jobId);
        if (!detail.isEmpty()) {
            doc.append("detail", new Document(detail));
        }
        return doc;
    }

    /**
     * Reads a stored document.
     *
     * @param doc the document
     * @return the event
     */
    public static AuditEvent fromDocument(Document doc) {
        Document detail = doc.get("detail", Document.class);
        return new AuditEvent(
                doc.getString("id"),
                AuditEventType.valueOf(doc.getString("type")),
                doc.getString("tenantId"),
                doc.getString("vendor"),
                doc.getString("exportBatchId"),
                Documents.intValue(doc, "attempt"),
                doc.getString("actor"),
                doc.getString("jobId"),
                Documents.instant(doc, "occurredAt"),
                detail == null ? Map.of() : detail);
    }

    /** Builds an event; the id and time are set at {@link #build()}. */
    public static final class Builder {
        private final AuditEventType type;
        private final String tenantId;
        private final String vendor;
        private String exportBatchId;
        private Integer attempt;
        private String actor = Actors.SYSTEM;
        private String jobId;
        private Instant occurredAt;
        private final Map<String, Object> detail = new LinkedHashMap<>();

        private Builder(AuditEventType type, String tenantId, String vendor) {
            this.type = type;
            this.tenantId = tenantId;
            this.vendor = vendor;
        }

        /** Sets the batch and attempt. */
        public Builder batch(String batchId, int batchAttempt) {
            this.exportBatchId = batchId;
            this.attempt = batchAttempt;
            return this;
        }

        /** Sets the actor; an operator's user id or one of {@link Actors}. */
        public Builder actor(String who) {
            this.actor = who;
            return this;
        }

        /** Sets the JobRunr job id that is writing the event. */
        public Builder jobId(String id) {
            this.jobId = id;
            return this;
        }

        /** Sets the time; defaults to now at build. */
        public Builder occurredAt(Instant when) {
            this.occurredAt = when;
            return this;
        }

        /** Adds one detail field; a null value is left out. */
        public Builder detail(String key, Object value) {
            if (value != null) {
                detail.put(key, value);
            }
            return this;
        }

        /** Adds every entry of a detail map. */
        public Builder details(Map<String, ?> values) {
            values.forEach(this::detail);
            return this;
        }

        /** Builds the event with a fresh v7 id. */
        public AuditEvent build() {
            return new AuditEvent(
                    Ids.eventId(),
                    type,
                    tenantId,
                    vendor,
                    exportBatchId,
                    attempt,
                    actor,
                    jobId,
                    occurredAt == null ? Instant.now() : occurredAt,
                    detail);
        }
    }
}
