package com.certifyos.vendor_exchange;

import java.util.HashMap;
import java.util.Map;

/**
 * Config values shared by both test profiles. {@code VendorExchangeConfig} validates every
 * registered mapping at startup regardless of whether a test injects it, so every key without a
 * default needs a value here, not only in the tests that use it.
 */
public final class TestConfig {

    private TestConfig() {}

    /**
     * Returns the common overrides for a test profile.
     *
     * @return a mutable map a profile can add to
     */
    public static Map<String, String> common() {
        Map<String, String> values = new HashMap<>();
        values.put("quarkus.oidc.tenant-enabled", "false");
        values.put("vendor-exchange.vendor-bucket", "test-vendor-bucket");
        values.put("vendor-exchange.egress.url", "http://localhost:0");
        values.put("vendor-exchange.egress.iap-client-id", "test-egress-iap-client-id");
        values.put("vendor-exchange.dal.url", "http://localhost:0");
        values.put("vendor-exchange.dal.iap-client-id", "test-dal-iap-client-id");
        values.put("vendor-exchange.api-layer.url", "http://localhost:0");
        return values;
    }
}
