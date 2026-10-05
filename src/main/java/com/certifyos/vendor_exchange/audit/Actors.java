package com.certifyos.vendor_exchange.audit;

/**
 * The actor strings the design fixes for audit events and system writes. Operator actions carry the
 * operator's user id instead of one of these.
 */
public final class Actors {

    /** The tick, the jobs and the reconciler. */
    public static final String SYSTEM = "system:vendor-export";

    /** The egress completion event handler. */
    public static final String EGRESS_EVENT = "system:egress-event";

    private Actors() {}
}
