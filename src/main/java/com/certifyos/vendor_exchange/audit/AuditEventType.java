package com.certifyos.vendor_exchange.audit;

/**
 * The audit event catalogue from the design's per-event contracts table: 24 types, each committed
 * either inside the transition it describes or as a standalone fact. {@link Scope} says whether the
 * event belongs to a schedule (found by tenant and vendor) or to a batch (carries the batch id).
 */
public enum AuditEventType {
    SCHEDULE_CREATED(Scope.SCHEDULE),
    SCHEDULE_UPDATED(Scope.SCHEDULE),
    SCHEDULE_ENABLED(Scope.SCHEDULE),
    SCHEDULE_RUN_NOW(Scope.SCHEDULE),
    SCHEDULE_DISABLED(Scope.SCHEDULE),
    EXPORT_TICK_COMPLETED(Scope.SERVICE),
    EXPORT_BATCH_SCHEDULED(Scope.BATCH),
    NPIS_REGISTERED(Scope.BATCH),
    EXPORT_SELECTION_COMPLETED(Scope.BATCH),
    EXPORT_BATCH_EMPTY(Scope.BATCH),
    EXPORT_EGRESS_REQUESTED(Scope.BATCH),
    EXPORT_EVENT_RECEIVED(Scope.BATCH),
    EXPORT_EVENT_REJECTED(Scope.SERVICE),
    EXPORT_EVENT_MISSED(Scope.BATCH),
    EXPORT_EGRESS_STALE(Scope.BATCH),
    EXPORT_EGRESS_COMPLETED(Scope.BATCH),
    NPIS_RECONCILED(Scope.BATCH),
    EXPORT_BATCH_DELIVERED(Scope.BATCH),
    EXPORT_BATCH_FAILED(Scope.BATCH),
    EXPORT_RETRY_REQUESTED(Scope.BATCH),
    EXPORT_PRIOR_ATTEMPT_CANCELLED(Scope.BATCH),
    EXPORT_PRIOR_ATTEMPT_CANCEL_REJECTED(Scope.BATCH),
    EXPORT_BATCH_SUPERSEDED(Scope.BATCH),
    EXPORT_BATCH_ACKNOWLEDGED(Scope.BATCH);

    /** What an event is about, which decides which ids it must carry. */
    public enum Scope {
        /** A schedule: tenant and vendor set, no batch id. */
        SCHEDULE,
        /** A batch: tenant, vendor and batch id set. */
        BATCH,
        /** The service itself (a tick) or a message that matched no batch: tenant and batch optional. */
        SERVICE
    }

    private final Scope scope;

    AuditEventType(Scope scope) {
        this.scope = scope;
    }

    /** The scope this type belongs to. */
    public Scope scope() {
        return scope;
    }

    /** Whether events of this type must carry an export batch id. */
    public boolean requiresBatch() {
        return scope == Scope.BATCH;
    }

    /** Whether events of this type must carry a tenant and a vendor. */
    public boolean requiresTenant() {
        return scope != Scope.SERVICE;
    }
}
