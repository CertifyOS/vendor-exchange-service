package com.certifyos.vendor_exchange.config;

import com.certifyos.vendor_exchange.ApiTestProfile;
import com.certifyos.vendor_exchange.MongoResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The service boots as the api role: mapping resolved, JobRunr server off, health open. */
@QuarkusTest
@QuarkusTestResource(MongoResource.class)
@TestProfile(ApiTestProfile.class)
public class ApiRoleBootIT {

    @Inject
    VendorExchangeConfig cfg;

    @Inject
    JobScheduler scheduler;

    @Test
    void mappingIsResolvedFromTheProfile() {
        Assertions.assertEquals("test-vendor-bucket", cfg.vendorBucket());
        Assertions.assertEquals("test-dal-iap-client-id", cfg.dal().iapClientId());
        Assertions.assertTrue(cfg.enabled());
        Assertions.assertEquals(8, cfg.jobRetries());
    }

    @Test
    void swapInKeysResolveToAbsentThroughTheEmptyDefault() {
        // application.properties maps these to ${ENV:} so an unset environment variable is an empty
        // string, which the mapping must read as Optional.empty().
        Assertions.assertTrue(cfg.apiLayer().clientId().isEmpty());
        Assertions.assertTrue(cfg.apiLayer().clientSecret().isEmpty());
        Assertions.assertTrue(cfg.pubsub().pushServiceAccount().isEmpty());
        Assertions.assertTrue(cfg.pubsub().pushAudience().isEmpty());
    }

    @Test
    void jobRunrServerIsOffAndRetriesFollowTheMapping() {
        Assertions.assertFalse(
                ConfigProvider.getConfig().getValue("quarkus.jobrunr.background-job-server.enabled", Boolean.class));
        Assertions.assertEquals(
                8,
                ConfigProvider.getConfig().getValue("quarkus.jobrunr.jobs.default-number-of-retries", Integer.class));
        Assertions.assertNotNull(scheduler, "the scheduler bean exists on the api role so handlers can enqueue");
    }

    @Test
    void healthIsOpenWithoutAToken() {
        RestAssured.get("/q/health/live").then().statusCode(200);
    }

    @Test
    void operatorPathsRequireAuthentication() {
        RestAssured.get("/vendor-exports/schedules").then().statusCode(401);
    }
}
