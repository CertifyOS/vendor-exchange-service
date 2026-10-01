package com.certifyos.vendor_exchange.config;

import com.certifyos.vendor_exchange.persistence.Collections;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Every index the design names, created on every boot of either role. Safe: MongoDB joins an
 * in-progress identical build and is a no-op against an existing one, so several instances calling
 * {@code createIndex} with the same spec at once is fine (JobRunr does the same in this database).
 * These are correctness guards (uniqueness of batches and NPIs, idempotency of received events), so
 * they stay synchronous at boot. Any index added after go-live is built out of band first: a boot
 * time build over a large collection would hold the instance's health check hostage. Index options
 * never change in place (MongoDB refuses with IndexOptionsConflict); a changed index is a new name.
 */
@ApplicationScoped
public class MongoIndexes {

    private final Collections collections;

    public MongoIndexes(Collections collections) {
        this.collections = collections;
    }

    void onStart(@Observes StartupEvent event) {
        createAll();
    }

    /** Creates every index; idempotent. Public so a test can call it without a boot. */
    public void createAll() {
        // schedules: _id = tenant|vendor is unique by construction
        collections
                .schedules()
                .createIndex(Indexes.ascending("enabled", "nextDueAt"), new IndexOptions().name("schedules_due"));
        collections.schedules().createIndex(Indexes.ascending("tenantId"), new IndexOptions().name("schedules_tenant"));

        // batches
        collections
                .batches()
                .createIndex(
                        Indexes.ascending("tenantId", "vendor", "period", "seq"),
                        new IndexOptions().name("batches_period_seq_unique").unique(true));
        collections
                .batches()
                .createIndex(
                        Indexes.ascending("state", "updatedAt"), new IndexOptions().name("batches_state_updated_at"));
        collections
                .batches()
                .createIndex(
                        Indexes.ascending("egress.correlationId"),
                        new IndexOptions()
                                .name("batches_correlation_id")
                                .partialFilterExpression(Filters.exists("egress.correlationId")));
        collections
                .batches()
                .createIndex(
                        Indexes.ascending("tenantId", "deliveredAt"),
                        new IndexOptions().name("batches_tenant_delivered_at"));

        // npis: _id = batch|npi is unique by construction
        collections.npis().createIndex(Indexes.ascending("exportBatchId"), new IndexOptions().name("npis_batch"));
        collections
                .npis()
                .createIndex(Indexes.ascending("tenantId", "npi"), new IndexOptions().name("npis_tenant_npi"));

        // events
        collections
                .events()
                .createIndex(
                        Indexes.ascending("exportBatchId", "occurredAt"), new IndexOptions().name("events_by_batch"));
        collections
                .events()
                .createIndex(
                        Indexes.ascending("tenantId", "vendor", "occurredAt"),
                        new IndexOptions().name("events_by_tenant_vendor"));
        collections
                .events()
                .createIndex(
                        Indexes.ascending("detail.messageId"),
                        new IndexOptions()
                                .name("events_received_message_unique")
                                .unique(true)
                                .partialFilterExpression(Filters.eq("type", "EXPORT_EVENT_RECEIVED")));
    }
}
