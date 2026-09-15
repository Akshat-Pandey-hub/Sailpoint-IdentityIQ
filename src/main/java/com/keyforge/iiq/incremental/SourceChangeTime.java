package com.keyforge.iiq.incremental;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parses an <b>authoritative source-side change timestamp</b> string (as IdentityIQ returns it) into
 * an absolute {@link Instant}. Only used to position the incremental watermark — never to fabricate a
 * value. A string the source did not supply, or one that cannot be parsed, yields {@code null}, which
 * the {@link IncrementalFilter} treats conservatively (the record is kept, never silently skipped).
 *
 * <p>The verified sources for these strings are SCIM {@code meta.created}/{@code meta.lastModified}
 * and the TaskResult {@code launched}/{@code completed} fields — all ISO-8601 UTC instants ending in
 * {@code Z} (e.g. {@code "2025-07-27T04:00:28.298Z"}). A zone-less local value is, defensively,
 * interpreted as UTC; classic display strings (minute precision, session timezone) are not fed here.
 */
public final class SourceChangeTime {

    private SourceChangeTime() {
    }

    /** Parses an ISO-8601 instant to {@link Instant}, or {@code null} if absent/unparseable. */
    public static Instant parseIso(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String s = value.trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException withOffset) {
            try {
                // No offset present: SCIM change fields are always UTC; interpret defensively as UTC.
                return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /** The later of two instants, treating {@code null} as "no value". */
    public static Instant max(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
