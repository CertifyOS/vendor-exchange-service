package com.certifyos.vendor_exchange.config;

import com.certifyos.vendor_exchange.MongoResource;
import com.certifyos.vendor_exchange.WorkerTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
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

    @Test
    void workerProfileTurnsTheJobRunrServerOn() {
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
}
