package com.certifyos.vendor_exchange.config;

import com.certifyos.vendor_exchange.ApiTestProfile;
import com.certifyos.vendor_exchange.MongoResource;
import com.certifyos.vendor_exchange.persistence.Collections;
import com.mongodb.client.MongoCollection;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Every index the design names exists by name, with the right keys, uniqueness and partial filter. */
@QuarkusTest
@QuarkusTestResource(MongoResource.class)
@TestProfile(ApiTestProfile.class)
public class MongoIndexesIT {

    @Inject
    Collections collections;

    @Inject
    MongoIndexes indexes;

    private static Map<String, Document> byName(MongoCollection<Document> collection) {
        Map<String, Document> found = new HashMap<>();
        for (Document index : collection.listIndexes()) {
            found.put(index.getString("name"), index);
        }
        return found;
    }

    @Test
    void scheduleIndexes() {
        Map<String, Document> found = byName(collections.schedules());
        Assertions.assertEquals(
                new Document("enabled", 1).append("nextDueAt", 1),
                found.get("schedules_due").get("key"));
        Assertions.assertEquals(
                new Document("tenantId", 1), found.get("schedules_tenant").get("key"));
    }

    @Test
    void batchIndexes() {
        Map<String, Document> found = byName(collections.batches());
        Document periodSeq = found.get("batches_period_seq_unique");
        Assertions.assertEquals(
                new Document("tenantId", 1)
                        .append("vendor", 1)
                        .append("period", 1)
                        .append("seq", 1),
                periodSeq.get("key"));
        Assertions.assertTrue(periodSeq.getBoolean("unique", false));
        Assertions.assertEquals(
                new Document("state", 1).append("updatedAt", 1),
                found.get("batches_state_updated_at").get("key"));
        Assertions.assertEquals(
                new Document("egress.correlationId", 1),
                found.get("batches_correlation_id").get("key"));
        Assertions.assertNotNull(found.get("batches_correlation_id").get("partialFilterExpression"));
        Assertions.assertEquals(
                new Document("tenantId", 1).append("deliveredAt", 1),
                found.get("batches_tenant_delivered_at").get("key"));
    }

    @Test
    void npiIndexes() {
        Map<String, Document> found = byName(collections.npis());
        Assertions.assertEquals(
                new Document("exportBatchId", 1), found.get("npis_batch").get("key"));
        Assertions.assertEquals(
                new Document("tenantId", 1).append("npi", 1),
                found.get("npis_tenant_npi").get("key"));
    }

    @Test
    void eventIndexes() {
        Map<String, Document> found = byName(collections.events());
        Assertions.assertEquals(
                new Document("exportBatchId", 1).append("occurredAt", 1),
                found.get("events_by_batch").get("key"));
        Document received = found.get("events_received_message_unique");
        Assertions.assertTrue(received.getBoolean("unique", false));
        Assertions.assertEquals(
                new Document("type", "EXPORT_EVENT_RECEIVED"), received.get("partialFilterExpression", Document.class));
    }

    @Test
    void creatingAgainIsANoOp() {
        int before = byName(collections.batches()).size();
        indexes.createAll();
        Assertions.assertEquals(before, byName(collections.batches()).size());
    }

    @Test
    void noIndexExpiresDocuments() {
        // Seven-year retention (design, Data model): the one way Mongo deletes on its own is a TTL
        // index, so none of the four service collections may carry expireAfterSeconds.
        for (var collection : java.util.List.of(
                collections.schedules(), collections.batches(), collections.npis(), collections.events())) {
            for (Document index : collection.listIndexes()) {
                Assertions.assertFalse(
                        index.containsKey("expireAfterSeconds"),
                        collection.getNamespace().getCollectionName() + " index " + index.getString("name")
                                + " is a TTL index");
            }
        }
    }
}
