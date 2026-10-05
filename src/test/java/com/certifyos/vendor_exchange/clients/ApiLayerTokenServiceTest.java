package com.certifyos.vendor_exchange.clients;

import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApiLayerTokenServiceTest {

    /** A clock the test moves forward. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static WireMockServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private VendorExchangeConfig cfg;
    private VendorExchangeConfig.ApiLayer apiLayer;
    private MutableClock clock;

    @BeforeAll
    static void beforeAll() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void before() {
        server.resetAll();
        cfg = Mockito.mock(VendorExchangeConfig.class);
        apiLayer = Mockito.mock(VendorExchangeConfig.ApiLayer.class);
        Mockito.when(cfg.apiLayer()).thenReturn(apiLayer);
        Mockito.when(apiLayer.url()).thenReturn(server.baseUrl());
        Mockito.when(apiLayer.clientId()).thenReturn(Optional.of("client-1"));
        Mockito.when(apiLayer.clientSecret()).thenReturn(Optional.of("secret-1"));
        clock = new MutableClock();
    }

    private void stubToken(String token, long expiresIn) {
        server.stubFor(WireMock.post(WireMock.urlEqualTo(ApiLayerTokenService.TOKEN_PATH))
                .willReturn(WireMock.okJson("{\"accessToken\":\"" + token + "\",\"expiresIn\":" + expiresIn
                        + ",\"tokenType\":\"Bearer\"}")));
    }

    @Test
    void postsTheMachineClientAndCachesTheToken() {
        stubToken("t-1", 3600);
        ApiLayerTokenService service = new ApiLayerTokenService(cfg, mapper, clock);
        Assertions.assertEquals("t-1", service.accessToken());
        Assertions.assertEquals("t-1", service.accessToken());
        server.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ApiLayerTokenService.TOKEN_PATH))
                        .withHeader("Content-Type", WireMock.equalTo("application/json"))
                        .withRequestBody(
                                WireMock.equalToJson("{\"clientId\":\"client-1\",\"clientSecret\":\"secret-1\"}")));
    }

    @Test
    void refreshesOneMinuteBeforeExpiry() {
        stubToken("t-1", 600);
        ApiLayerTokenService service = new ApiLayerTokenService(cfg, mapper, clock);
        Assertions.assertEquals("t-1", service.accessToken());
        clock.advance(Duration.ofSeconds(500));
        Assertions.assertEquals("t-1", service.accessToken(), "still inside the window");
        clock.advance(Duration.ofSeconds(45));
        stubToken("t-2", 600);
        Assertions.assertEquals("t-2", service.accessToken(), "past expiry minus the margin");
        server.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(ApiLayerTokenService.TOKEN_PATH)));
    }

    @Test
    void notConfiguredThrowsWithoutCalling() {
        Mockito.when(apiLayer.clientSecret()).thenReturn(Optional.empty());
        ApiLayerTokenService service = new ApiLayerTokenService(cfg, mapper, clock);
        Assertions.assertFalse(service.configured());
        Assertions.assertThrows(ApiLayerNotConfiguredException.class, service::accessToken);
        server.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void refusalBecomesATokenExceptionNamingTheStatus() {
        server.stubFor(WireMock.post(WireMock.urlEqualTo(ApiLayerTokenService.TOKEN_PATH))
                .willReturn(WireMock.aResponse().withStatus(401).withBody("{\"error\":\"nope\"}")));
        ApiLayerTokenService service = new ApiLayerTokenService(cfg, mapper, clock);
        ApiLayerTokenException refused = Assertions.assertThrows(ApiLayerTokenException.class, service::accessToken);
        Assertions.assertTrue(refused.getMessage().contains("401"));
        Assertions.assertFalse(refused.getMessage().contains("secret-1"));
    }
}
