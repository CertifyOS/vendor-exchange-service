package com.certifyos.vendor_exchange.clients;

import com.certifyos.vendor_exchange.auth.GoogleAuthFilter;
import com.certifyos.vendor_exchange.auth.GoogleIdTokenService;
import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import jakarta.inject.Singleton;

/** The IAP token for the egress backend. */
@Singleton
public class EgressAuthFilter extends GoogleAuthFilter {

    private final VendorExchangeConfig cfg;

    public EgressAuthFilter(GoogleIdTokenService tokens, VendorExchangeConfig cfg) {
        super(tokens);
        this.cfg = cfg;
    }

    @Override
    protected String audience() {
        return cfg.egress().iapClientId();
    }
}
