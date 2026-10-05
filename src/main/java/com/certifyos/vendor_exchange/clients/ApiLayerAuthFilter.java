package com.certifyos.vendor_exchange.clients;

import com.certifyos.vendor_exchange.auth.GoogleIdTokenService;
import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Optional;

/**
 * Adds the Auth0 bearer to every api-layer call and, when api-layer sits behind IAP (it does on
 * internal, with a Google-managed OAuth client), a Google ID token for that backend in {@code
 * Proxy-Authorization}, the header IAP reads when the application needs {@code Authorization} for
 * itself. The tenant header is a method parameter on the client.
 */
@ApplicationScoped
public class ApiLayerAuthFilter implements ClientRequestFilter {

    /** The header IAP accepts its token in when Authorization is taken. */
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    private final ApiLayerTokenService tokens;
    private final GoogleIdTokenService googleTokens;
    private final VendorExchangeConfig cfg;

    public ApiLayerAuthFilter(
            ApiLayerTokenService tokens, GoogleIdTokenService googleTokens, VendorExchangeConfig cfg) {
        this.tokens = tokens;
        this.googleTokens = googleTokens;
        this.cfg = cfg;
    }

    @Override
    public void filter(ClientRequestContext request) throws IOException {
        try {
            request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
            Optional<String> iapAudience = cfg.apiLayer().iapAudience();
            if (iapAudience.isPresent()) {
                request.getHeaders()
                        .putSingle(PROXY_AUTHORIZATION, "Bearer " + googleTokens.idToken(iapAudience.get()));
            }
        } catch (RuntimeException failure) {
            throw new IOException("could not obtain an api-layer token", failure);
        }
    }
}
