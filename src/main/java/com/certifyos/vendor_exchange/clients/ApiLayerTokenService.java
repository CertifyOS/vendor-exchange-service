package com.certifyos.vendor_exchange.clients;

import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * An Auth0 access token for api-layer, obtained through api-layer's own {@code POST
 * /auth/client-credentials} with this service's machine client and cached until one minute before
 * it expires. Until the client id and secret exist (a swap-in point), {@link #configured()} is
 * false, {@link #accessToken()} throws {@link ApiLayerNotConfiguredException}, and the readiness
 * check reports the gap.
 */
@ApplicationScoped
public class ApiLayerTokenService {

    static final String TOKEN_PATH = "/auth/client-credentials";
    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOG = Logger.getLogger(ApiLayerTokenService.class);

    private final VendorExchangeConfig cfg;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http;
    private volatile Cached cached;

    public ApiLayerTokenService(VendorExchangeConfig cfg, ObjectMapper mapper, Clock clock) {
        this.cfg = cfg;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** Whether the machine client exists in configuration. */
    public boolean configured() {
        return cfg.apiLayer().clientId().isPresent()
                && cfg.apiLayer().clientSecret().isPresent();
    }

    /**
     * A valid access token.
     *
     * @return the token value, to send as a bearer
     * @throws ApiLayerNotConfiguredException when the machine client is absent
     * @throws ApiLayerTokenException when api-layer refuses or cannot be reached
     */
    public String accessToken() {
        if (!configured()) {
            throw new ApiLayerNotConfiguredException();
        }
        Cached current = cached;
        if (current != null && clock.instant().isBefore(current.expiresAt())) {
            return current.token();
        }
        return refresh();
    }

    private synchronized String refresh() {
        Cached current = cached;
        if (current != null && clock.instant().isBefore(current.expiresAt())) {
            return current.token();
        }
        TokenResponse response = fetch();
        long seconds = response.expiresIn() == null ? 0 : response.expiresIn();
        Instant expiresAt = clock.instant().plusSeconds(seconds).minus(EXPIRY_MARGIN);
        cached = new Cached(response.accessToken(), expiresAt);
        LOG.infof("api-layer token obtained, valid until %s", expiresAt);
        return response.accessToken();
    }

    private TokenResponse fetch() {
        TokenRequest body = new TokenRequest(
                cfg.apiLayer().clientId().orElseThrow(),
                cfg.apiLayer().clientSecret().orElseThrow());
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(cfg.apiLayer().url() + TOKEN_PATH))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ApiLayerTokenException("client-credentials answered " + response.statusCode());
            }
            TokenResponse token = mapper.readValue(response.body(), TokenResponse.class);
            if (token.accessToken() == null || token.accessToken().isBlank()) {
                throw new ApiLayerTokenException("client-credentials answered without an access token");
            }
            return token;
        } catch (IOException failure) {
            throw new ApiLayerTokenException("client-credentials call failed: " + failure.getMessage(), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiLayerTokenException("client-credentials call interrupted", interrupted);
        }
    }

    record Cached(String token, Instant expiresAt) {}

    record TokenRequest(String clientId, String clientSecret) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, Long expiresIn, String tokenType) {}
}
