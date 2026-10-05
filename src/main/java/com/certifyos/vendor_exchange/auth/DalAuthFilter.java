package com.certifyos.vendor_exchange.auth;

import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import jakarta.enterprise.context.ApplicationScoped;

/** The IAP token for the DAL backend. */
@ApplicationScoped
public class DalAuthFilter extends GoogleAuthFilter {

    private final VendorExchangeConfig cfg;

    public DalAuthFilter(GoogleIdTokenService tokens, VendorExchangeConfig cfg) {
        super(tokens);
        this.cfg = cfg;
    }

    @Override
    protected String audience() {
        return cfg.dal().iapClientId();
    }
}
