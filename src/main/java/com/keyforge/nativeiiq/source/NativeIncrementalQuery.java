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

    /**
     * The native creation-time property — the watermark cursor for event-shaped, append-only objects
     * (AuditEvent, SyslogEvent, WorkItemArchive, CertificationArchive, Access History records), which are
     * written once and never modified, so "new since" is exactly {@code created > bound}.
     */
    public static final String CREATED_PROPERTY = "created";

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
            qo.addFilter(changedAfter(modifiedAfter));
        }
        return qo;
    }

    /**
     * "Changed after {@code bound}": {@code modified > bound}, OR never modified and {@code created > bound}.
     * IdentityIQ leaves {@code modified} NULL until an object is first updated (in the reference data 29 of 37
     * ManagedAttributes and 101 of 208 TaskResults), so a bare {@code modified >} predicate would silently miss
     * newly created, never-edited objects. Built only from {@code Filter.gt/isnull/and/or} (verified in the 8.4
     * {@code identityiq.jar}).
     */
    static Filter changedAfter(Date bound) {
        return Filter.or(Filter.gt(MODIFIED_PROPERTY, bound),
                Filter.and(Filter.isnull(MODIFIED_PROPERTY), Filter.gt(CREATED_PROPERTY, bound)));
    }

    /**
     * Adds a server-side {@code created > createdAfter} filter to {@code qo} when a bound is supplied (same
     * strict {@code >} and overlap convention as {@link #applyModifiedAfter}). A {@code null} {@code qo} or
     * {@code null} {@code createdAfter} is a no-op (full scan).
     *
     * @return the same {@link QueryOptions} instance, for fluent use
     */
    public static QueryOptions applyCreatedAfter(QueryOptions qo, Date createdAfter) {
        if (qo != null && createdAfter != null) {
            qo.addFilter(Filter.gt(CREATED_PROPERTY, createdAfter));
        }
        return qo;
    }

    /**
     * Parses an ISO-8601 instant (e.g. {@code 2026-01-01T00:00:00Z}, with or without offset) into a
     * {@link Date} suitable for an IIQ {@code modified} filter, or {@code null} if absent/unparseable.
     * Self-contained so the plugin needs no loader classes. A value that cannot be parsed yields
     * {@code null}; resources must reject such a value with {@link #isInvalid} first, so a malformed
     * bound is answered with 400 and never collapses into an unfiltered full scan.
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

    /** True when {@code iso} was supplied (non-blank) but is not a parseable ISO-8601 instant. */
    public static boolean isInvalid(String iso) {
        return iso != null && !iso.trim().isEmpty() && parseIsoToDate(iso) == null;
    }

    /** The client error for an invalid bound; a fixed message that never echoes input or internals. */
    public static IllegalArgumentException invalidTimestamp() {
        return new IllegalArgumentException("invalid 'modifiedAfter': expected an ISO-8601 instant, e.g. "
                + "2026-10-12T10:00:00Z or 2026-10-12T15:30:00+05:30");
    }
}
