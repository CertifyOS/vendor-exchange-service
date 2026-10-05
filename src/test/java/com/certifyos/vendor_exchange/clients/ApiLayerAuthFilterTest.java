package com.certifyos.vendor_exchange.clients;

import com.certifyos.vendor_exchange.auth.GoogleIdTokenService;
import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApiLayerAuthFilterTest {

    private ApiLayerTokenService tokens;
    private GoogleIdTokenService google;
    private VendorExchangeConfig.ApiLayer apiLayer;
    private ApiLayerAuthFilter filter;
    private MultivaluedMap<String, Object> headers;
    private ClientRequestContext request;

    @BeforeEach
    void before() {
        tokens = Mockito.mock(ApiLayerTokenService.class);
        google = Mockito.mock(GoogleIdTokenService.class);
        VendorExchangeConfig cfg = Mockito.mock(VendorExchangeConfig.class);
        apiLayer = Mockito.mock(VendorExchangeConfig.ApiLayer.class);
        Mockito.when(cfg.apiLayer()).thenReturn(apiLayer);
        Mockito.when(tokens.accessToken()).thenReturn("auth0-token");
        Mockito.when(google.idToken("/projects/1/global/backendServices/2")).thenReturn("iap-token");
        filter = new ApiLayerAuthFilter(tokens, google, cfg);
        headers = new MultivaluedHashMap<>();
        request = Mockito.mock(ClientRequestContext.class);
        Mockito.when(request.getHeaders()).thenReturn(headers);
    }

    @Test
    void sendsBothTokensWhenApiLayerIsBehindIap() throws IOException {
        Mockito.when(apiLayer.iapAudience()).thenReturn(Optional.of("/projects/1/global/backendServices/2"));
        filter.filter(request);
        Assertions.assertEquals("Bearer auth0-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals("Bearer iap-token", headers.getFirst(ApiLayerAuthFilter.PROXY_AUTHORIZATION));
    }

    @Test
    void sendsOnlyTheAuth0BearerWithoutAnIapAudience() throws IOException {
        Mockito.when(apiLayer.iapAudience()).thenReturn(Optional.empty());
        filter.filter(request);
        Assertions.assertEquals("Bearer auth0-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertFalse(headers.containsKey(ApiLayerAuthFilter.PROXY_AUTHORIZATION));
        Mockito.verifyNoInteractions(google);
    }

    @Test
    void aTokenFailureIsAnIoExceptionForTheCaller() {
        Mockito.when(apiLayer.iapAudience()).thenReturn(Optional.empty());
        Mockito.when(tokens.accessToken()).thenThrow(new ApiLayerNotConfiguredException());
        Assertions.assertThrows(IOException.class, () -> filter.filter(request));
    }
}
