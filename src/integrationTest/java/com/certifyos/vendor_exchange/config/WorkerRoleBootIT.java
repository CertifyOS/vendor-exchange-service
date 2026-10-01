package com.certifyos.vendor_exchange.config;

import com.certifyos.vendor_exchange.MongoResource;
import com.certifyos.vendor_exchange.WorkerTestProfile;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The service boots as the worker role: the JobRunr background server runs and registers itself. */
@QuarkusTest
@QuarkusTestResource(MongoResource.class)
@TestProfile(WorkerTestProfile.class)
public class WorkerRoleBootIT {

    @Inject
    StorageProvider storage;

    @Inject
    MongoClient mongo;

    @Test
    void workerProfileFlagsAreSet() {
        Assertions.assertTrue(
                ConfigProvider.getConfig().getValue("quarkus.jobrunr.background-job-server.enabled", Boolean.class));
        Assertions.assertFalse(
                ConfigProvider.getConfig().getValue("quarkus.jobrunr.dashboard.enabled", Boolean.class),
                "the test profile must keep the dashboard port closed");
    }

    @Test
    void backgroundJobServerRegistersItsHeartbeat() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> !storage.getBackgroundJobServers()
                .isEmpty());
    }

    @Test
    void jobRunrCollectionsCarryThePrefixTheDesignDocNames() {
        // Without table-prefix JobRunr 8.8.2 creates jobs, recurring_jobs, background_job_servers,
        // metadata and migrations (file-ingestion finding 72). The prefix in application.properties
        // gives them the jobrunr_ names the design doc and the runbook use.
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> !storage.getBackgroundJobServers()
                .isEmpty());
        List<String> names =
                mongo.getDatabase("vendor_exchange").listCollectionNames().into(new java.util.ArrayList<>());
        Assertions.assertTrue(names.contains("jobrunr_background_job_servers"), "collections: " + names);
        Assertions.assertTrue(names.contains("jobrunr_metadata"), "collections: " + names);
        Assertions.assertTrue(
                names.stream().filter(n -> !n.startsWith("jobrunr_")).noneMatch(n -> n.contains("job")),
                "no unprefixed JobRunr collection expected: " + names);
    }
}
