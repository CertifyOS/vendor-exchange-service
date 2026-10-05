package com.certifyos.vendor_exchange.export.api;

import java.time.Instant;

/** One schedule in a list answer. */
public record ScheduleSummary(String id, String tenantId, String vendor, boolean enabled, Instant nextDueAt) {}
