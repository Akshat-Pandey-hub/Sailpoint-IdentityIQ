package com.keyforge.nativeiiq.source;

import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * Server-side CSS incremental support for the native Java-API extractors. Unlike the REST/SCIM path —
 * which has no "modified since" query filter and must read every page and filter client-side — the
 * native {@link sailpoint.api.SailPointContext} search supports a real server-side predicate, so an
 * incremental native extraction can ask IdentityIQ to return <b>only</b> the objects changed after a
 * watermark, reducing reads (not just writes).
 *
 * <p>The predicate is {@code Filter.gt("modified", watermark)} on the standard
 * {@code sailpoint.object.SailPointObject.modified} column (verified against the 8.4
 * {@code identityiq.jar}: {@code Filter.gt(String, Object)} + {@code QueryOptions.addFilter(Filter)}).
 * Strict {@code >}; the caller already lowers the watermark by the configured overlap window before
 * passing it here, so boundary records are re-admitted and the idempotent upserts absorb the harmless
 * re-processing. A {@code null} watermark means "no bound" (full scan) and the query is left unchanged.
 *
 * <p>This helper is intentionally the single place the native {@code modified} predicate is built, so
 * the JAR API is verified once rather than copied into every extractor. Pure and self-contained (no
 * dependency on the KeyForge loader module), since it is compiled into the IIQ plugin.
 */
public final class NativeIncrementalQuery {

    /** The native source change-time property name — the watermark cursor for CSS incremental. */
    public static final String MODIFIED_PROPERTY = "modified";

    private NativeIncrementalQuery() {
    }

    /**
     * Adds a server-side {@code modified > modifiedAfter} filter to {@code qo} when a watermark is
     * supplied. A {@code null} {@code qo} or {@code null} {@code modifiedAfter} is a no-op (full scan).
     *
     * @return the same {@link QueryOptions} instance, for fluent use
     */
    public static QueryOptions applyModifiedAfter(QueryOptions qo, Date modifiedAfter) {
        if (qo != null && modifiedAfter != null) {
            qo.addFilter(Filter.gt(MODIFIED_PROPERTY, modifiedAfter));
        }
        return qo;
    }

    /**
     * Parses an ISO-8601 instant (e.g. {@code 2026-01-01T00:00:00Z}, with or without offset) into a
     * {@link Date} suitable for an IIQ {@code modified} filter, or {@code null} if absent/unparseable.
     * Self-contained so the plugin needs no loader classes. A value that cannot be parsed yields
     * {@code null}, which collapses to a full scan — the helper never fabricates a boundary.
     */
    public static Date parseIsoToDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return null;
        }
        String s = iso.trim();
        try {
            return Date.from(Instant.parse(s));
        } catch (Exception withZ) {
            try {
                return Date.from(OffsetDateTime.parse(s).toInstant());
            } catch (Exception withOffset) {
                return null;
            }
        }
    }
}
