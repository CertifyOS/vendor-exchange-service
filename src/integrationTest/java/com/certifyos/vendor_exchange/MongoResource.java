package com.certifyos.vendor_exchange;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.MongoDBContainer;

/**
 * One MongoDB 7 replica set (Testcontainers) for the whole test JVM; transactions need a replica
 * set. This resource supplies config values only. It cannot select the active profile; that is
 * what {@link ApiTestProfile} and {@link WorkerTestProfile} are for.
 */
public class MongoResource implements QuarkusTestResourceLifecycleManager {

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Override
    public Map<String, String> start() {
        MONGO.start();
        return Map.of("quarkus.mongodb.connection-string", MONGO.getReplicaSetUrl("vendor_exchange"));
    }

    @Override
    public void stop() {
        MONGO.stop();
    }
}
