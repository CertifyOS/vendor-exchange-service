package com.certifyos.vendor_exchange.export.api;

import java.time.Instant;

/** One export batch in a list answer. */
public record BatchSummary(String id, String tenantId, String vendor, String state, Instant createdAt) {}
