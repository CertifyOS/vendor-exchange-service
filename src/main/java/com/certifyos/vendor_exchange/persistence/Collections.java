package com.certifyos.vendor_exchange.persistence;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

/**
 * The four collections this service owns in database {@code vendor_exchange}. JobRunr owns five more
 * ({@code jobrunr_*}) in the same database and our code never names them.
 */
@ApplicationScoped
public class Collections {

    /** Database name; also the JobRunr database and the Testcontainers replica set database. */
    public static final String DATABASE = "vendor_exchange";

    private final MongoClient client;

    public Collections(MongoClient client) {
        this.client = client;
    }

    MongoDatabase database() {
        return client.getDatabase(DATABASE);
    }

    /** One document per tenant and vendor. */
    public MongoCollection<Document> schedules() {
        return database().getCollection("vendor_export_schedules");
    }

    /** One document per tenant, vendor, period and sequence. */
    public MongoCollection<Document> batches() {
        return database().getCollection("vendor_export_batches");
    }

    /** One document per NPI within a batch; written once. */
    public MongoCollection<Document> npis() {
        return database().getCollection("vendor_export_npis");
    }

    /** Append-only audit trail; one document per lifecycle event. */
    public MongoCollection<Document> events() {
        return database().getCollection("vendor_export_events");
    }
}
