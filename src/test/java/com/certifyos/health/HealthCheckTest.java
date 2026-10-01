package com.certifyos.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthCheckTest {
    @Test
    public void testLivenessEndpoint() {
        given().when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.name", hasItems("Application liveness check"))
                .body("checks.status", everyItem(is("UP")));
    }

    @Test
    public void testReadinessEndpoint() {
        given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    public void testCombinedHealthEndpoint() {
        given().when().get("/q/health").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    public void testWellnessEndpoint() {
        given().when().get("/q/health/well").then().statusCode(200).body("status", is("UP"));
    }
}
