package com.certifyos.vendor_exchange.auth;

/**
 * The two operator permissions from the design, as DAL resource and action. Checked per endpoint
 * only when {@code vendor-exchange.permissions.enforce} is true.
 */
public enum Permission {
    /** Create, change, run and retry exports. */
    MANAGE("vendor-export", "manage"),
    /** Read schedules, batches and audit. */
    READ("vendor-export", "read");

    private final String resource;
    private final String action;

    Permission(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }

    /** The DAL resource name. */
    public String resource() {
        return resource;
    }

    /** The DAL action name. */
    public String action() {
        return action;
    }
}
