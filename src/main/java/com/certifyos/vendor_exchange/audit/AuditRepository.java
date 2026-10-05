package com.certifyos.vendor_exchange.audit;

import com.certifyos.vendor_exchange.persistence.Collections;
import com.certifyos.vendor_exchange.persistence.Documents;
import com.mongodb.MongoWriteException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

/**
 * The audit trail: append-only, one document per event, kept seven years. Three ways to write, one
 * per commit rule in the design: inside a transition's transaction, standalone, or standalone and
 * idempotent on a natural key (a received message id). Nothing here ever updates or deletes.
 */
@ApplicationScoped
public class AuditRepository {

    /** The logger the structured audit line goes to; alerts count these lines in Cloud Logging. */
    public static final String AUDIT_LOGGER = "audit";

    /** The detail keys the design's alerts evaluate on; echoed on the line when the event carries them. */
    static final java.util.List<String> ALERT_KEYS =
            java.util.List.of("cause", "completionSource", "match", "reason", "check", "trigger");

    private static final Logger AUDIT = Logger.getLogger(AUDIT_LOGGER);

    private final Collections collections;

    public AuditRepository(Collections collections) {
        this.collections = collections;
    }

    /**
     * The one line per audit event, after the write: {@code AUDIT type=... tenantId=... vendor=...
     * exportBatchId=... attempt=... actor=...} plus the alert keys the event carries. Ids and
     * enumerations only, never a practitioner identifier.
     *
     * @param event the event written
     * @return the line
     */
    static String line(AuditEvent event) {
        StringBuilder out = new StringBuilder("AUDIT type=").append(event.type());
        out.append(" tenantId=").append(event.tenantId());
        out.append(" vendor=").append(event.vendor());
        out.append(" exportBatchId=").append(event.exportBatchId());
        out.append(" attempt=").append(event.attempt());
        out.append(" actor=").append(event.actor());
        for (String key : ALERT_KEYS) {
            Object value = event.detail().get(key);
            if (value != null) {
                out.append(' ').append(key).append('=').append(value);
            }
        }
        return out.toString();
    }

    private static void log(AuditEvent event) {
        AUDIT.info(line(event));
    }

    /**
     * Writes an event inside a transaction, so it commits or rolls back with the state change.
     *
     * @param session the transaction
     * @param event the event
     */
    public void write(ClientSession session, AuditEvent event) {
        collections.events().insertOne(session, event.toDocument());
    }

    /**
     * Writes a standalone fact, outside any transaction.
     *
     * @param event the event
     */
    public void write(AuditEvent event) {
        collections.events().insertOne(event.toDocument());
    }

    /**
     * Writes a standalone fact that is idempotent on a natural key held in {@code detail} and
     * enforced by a partial unique index ({@code EXPORT_EVENT_RECEIVED} on {@code detail.messageId}).
     * A duplicate is swallowed and reported as {@code false}; any other failure propagates.
     *
     * @param event the event
     * @return true when the event was new, false when the same key was already recorded
     */
    public boolean writeIdempotent(AuditEvent event) {
        try {
            collections.events().insertOne(event.toDocument());
            log(event);
            return true;
        } catch (MongoWriteException failure) {
            if (Documents.isDuplicateKey(failure)) {
                return false;
            }
            throw failure;
        }
    }

    /**
     * A batch's events in the order they happened.
     *
     * @param exportBatchId the batch
     * @param limit the most rows to return
     * @return the events, oldest first
     */
    public List<AuditEvent> findForBatch(String exportBatchId, int limit) {
        return toList(Filters.eq("exportBatchId", exportBatchId), limit);
    }

    /**
     * A schedule's events, including those of its batches, newest first.
     *
     * @param tenantId the tenant
     * @param vendor the vendor
     * @param limit the most rows to return
     * @return the events, newest first
     */
    public List<AuditEvent> findForSchedule(String tenantId, String vendor, int limit) {
        Bson filter = Filters.and(Filters.eq("tenantId", tenantId), Filters.eq("vendor", vendor));
        List<AuditEvent> events = new ArrayList<>();
        for (Document doc : collections
                .events()
                .find(filter)
                .sort(Sorts.descending("occurredAt"))
                .limit(limit)) {
            events.add(AuditEvent.fromDocument(doc));
        }
        return events;
    }

    private List<AuditEvent> toList(Bson filter, int limit) {
        List<AuditEvent> events = new ArrayList<>();
        for (Document doc : collections
                .events()
                .find(filter)
                .sort(Sorts.ascending("occurredAt"))
                .limit(limit)) {
            events.add(AuditEvent.fromDocument(doc));
        }
        return events;
    }
}
