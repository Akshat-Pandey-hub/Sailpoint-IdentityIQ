package com.keyforge.iiq.auditevent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps an {@link AuditEvent} (from {@code /analyze/audit/auditDataSource.json}) to a
 * {@link AuditEventRow}. Pure and DB-free.
 *
 * <p>Faithful, evidence-first mapping — nothing invented:
 * <ul>
 *   <li>{@code auditid} ← canonicalised {@code id} (32-hex IIQ id → dashed UUID).</li>
 *   <li>{@code action}/{@code source}/{@code target} ← preserved exactly as supplied
 *       ({@code source}/{@code target} blanked to NULL only when genuinely empty; {@code action}
 *       is stored verbatim).</li>
 *   <li>{@code createdDisplay} ← the raw {@code created} string, verbatim.</li>
 *   <li>{@code createdAt} ← parsed <b>only</b> with the one verified pattern
 *       {@code "MMMM d, yyyy, h:mm a"} (US locale). No seconds and no timezone exist in the
 *       source, so the result is a naive minute-precision wall-clock; when the string does not
 *       match, {@code createdAt} is {@code null} (a reported parse limitation, never a fabricated
 *       value).</li>
 * </ul>
 * No {@code action_normalized} taxonomy is derived — that mapping is not yet PDF-verified.
 */
public final class AuditEventRowMapper {

    /**
     * The one verified {@code created} format, e.g. {@code "June 27, 2026, 2:01 AM"}.
     * {@code h} = 1-12 hour, {@code a} = AM/PM. US locale for the English month names.
     */
    static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.US);

    private AuditEventRowMapper() {
    }

    public static AuditEventRow map(AuditEvent e) {
        return new AuditEventRow(
                toCanonicalUuid(e.id()),
                // action is preserved exactly (verified always present); no taxonomy applied
                e.action(),
                blankToNull(e.source()),
                blankToNull(e.target()),
                blankToNull(e.created()),
                parseCreated(e.created()));
    }

    /**
     * Parses the {@code created} display string with the single verified pattern, returning a
     * naive {@link LocalDateTime} (minute precision). Returns {@code null} — never a guess — when
     * the value is blank or does not match, so the caller can report the limitation.
     */
    static LocalDateTime parseCreated(String created) {
        if (created == null || created.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(created.trim(), CREATED_FORMAT);
        } catch (DateTimeParseException notTheVerifiedFormat) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new AuditEventMappingException("IdentityIQ AuditEvent id is missing; cannot form a UUID key.");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new AuditEventMappingException("IdentityIQ AuditEvent id '" + rawId
                    + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
