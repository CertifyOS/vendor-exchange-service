package com.certifyos.vendor_exchange.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Every identifier this service mints, in one place, so the shapes the design doc fixes are written
 * once. Batch ids use hyphens only: the vendor filename uses underscores as separators, so a tenant
 * id containing an underscore is refused at the schedule boundary and again here.
 */
public final class Ids {

    private static final TimeBasedEpochGenerator EVENT_IDS = Generators.timeBasedEpochGenerator();
    private static final Pattern NO_UNDERSCORE = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern VENDOR = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern NPI = Pattern.compile("^\\d{10}$");

    private Ids() {}

    /**
     * Audit event id: {@code ev-<uuid v7>}. Version 7 carries a millisecond timestamp in its high
     * bits, so new ids sort after old ones at the right edge of every index they start.
     *
     * @return a new event id
     */
    public static String eventId() {
        return "ev-" + EVENT_IDS.generate();
    }

    /**
     * Schedule id: {@code <tenantId>|<vendor>}; one schedule per pair.
     *
     * @param tenantId the tenant
     * @param vendor the vendor code, lower case
     * @return the schedule id
     */
    public static String scheduleId(String tenantId, String vendor) {
        return requireTenant(tenantId) + "|" + requireVendor(vendor);
    }

    /**
     * Batch id: {@code <tenantId>-<vendor>-<yyyy-MM>-<seq>} with a three-digit sequence.
     *
     * @param tenantId the tenant, without underscores
     * @param vendor the vendor code
     * @param period the month the batch is for
     * @param seq 1 for the first batch of a period, incremented by supersede
     * @return the batch id
     */
    public static String batchId(String tenantId, String vendor, YearMonth period, int seq) {
        if (seq < 1 || seq > 999) {
            throw new IllegalArgumentException("seq must be 1..999, got " + seq);
        }
        return requireTenant(tenantId) + "-" + requireVendor(vendor) + "-" + period + "-"
                + String.format(Locale.ROOT, "%03d", seq);
    }

    /**
     * NPI registry id: {@code <exportBatchId>|<npi>}, so a repeated upsert is a no-op.
     *
     * @param exportBatchId the batch
     * @param npi the ten-digit NPI
     * @return the registry id
     */
    public static String npiId(String exportBatchId, String npi) {
        if (!NPI.matcher(npi).matches()) {
            throw new IllegalArgumentException("npi must be ten digits, got " + npi);
        }
        return exportBatchId + "|" + npi;
    }

    /**
     * Egress correlation id: the batch id plus an attempt suffix, so a retry gives egress a new id
     * while the vendor keeps seeing one batch id.
     *
     * @param exportBatchId the batch
     * @param attempt 1 for the first attempt
     * @return the correlation id, for example {@code org-xyz-candor-2026-10-001-r1}
     */
    public static String correlationId(String exportBatchId, int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1, got " + attempt);
        }
        return exportBatchId + "-r" + attempt;
    }

    /**
     * The batch id a correlation id belongs to.
     *
     * @param correlationId a value produced by {@link #correlationId(String, int)}
     * @return the batch id
     */
    public static String batchIdOf(String correlationId) {
        int suffix = correlationId.lastIndexOf("-r");
        if (suffix < 1) {
            throw new IllegalArgumentException("not a correlation id: " + correlationId);
        }
        return correlationId.substring(0, suffix);
    }

    private static String requireTenant(String tenantId) {
        if (tenantId == null || !NO_UNDERSCORE.matcher(tenantId).matches()) {
            throw new IllegalArgumentException(
                    "tenant id must be letters, digits and hyphens only (the vendor filename uses underscores): "
                            + tenantId);
        }
        return tenantId;
    }

    private static String requireVendor(String vendor) {
        if (vendor == null || !VENDOR.matcher(vendor).matches()) {
            throw new IllegalArgumentException("vendor must be lower-case letters, digits and hyphens: " + vendor);
        }
        return vendor;
    }
}
