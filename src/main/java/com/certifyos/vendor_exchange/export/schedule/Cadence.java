package com.certifyos.vendor_exchange.export.schedule;

import com.certifyos.vendor_exchange.persistence.Documents;
import org.bson.Document;

/**
 * When a schedule is due. {@code MONTHLY} on a day 1 to 28 (so every month has the day) or a cron
 * expression. The next occurrence is computed by the schedule service in the schedule's timezone.
 *
 * @param type monthly or cron
 * @param dayOfMonth 1 to 28 when monthly, null otherwise
 * @param expression a cron expression when cron, null otherwise
 */
public record Cadence(Type type, Integer dayOfMonth, String expression) {

    /** Cadence kinds. */
    public enum Type {
        MONTHLY,
        CRON
    }

    public Cadence {
        if (type == null) {
            throw new IllegalArgumentException("cadence type is required");
        }
        if (type == Type.MONTHLY && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28)) {
            throw new IllegalArgumentException("monthly cadence needs dayOfMonth 1..28, got " + dayOfMonth);
        }
        if (type == Type.CRON && (expression == null || expression.isBlank())) {
            throw new IllegalArgumentException("cron cadence needs an expression");
        }
    }

    /**
     * A monthly cadence.
     *
     * @param dayOfMonth 1 to 28
     * @return the cadence
     */
    public static Cadence monthly(int dayOfMonth) {
        return new Cadence(Type.MONTHLY, dayOfMonth, null);
    }

    /**
     * A cron cadence.
     *
     * @param expression the cron expression
     * @return the cadence
     */
    public static Cadence cron(String expression) {
        return new Cadence(Type.CRON, null, expression);
    }

    /** The BSON shape: {@code { type, dayOfMonth? , expression? }}. */
    public Document toDocument() {
        Document doc = new Document("type", type.name().toLowerCase(java.util.Locale.ROOT));
        Documents.put(doc, "dayOfMonth", dayOfMonth);
        Documents.put(doc, "expression", expression);
        return doc;
    }

    /**
     * Reads the BSON shape.
     *
     * @param doc the nested cadence document
     * @return the cadence
     */
    public static Cadence fromDocument(Document doc) {
        Type type = Type.valueOf(doc.getString("type").toUpperCase(java.util.Locale.ROOT));
        return new Cadence(type, Documents.intValue(doc, "dayOfMonth"), doc.getString("expression"));
    }
}
