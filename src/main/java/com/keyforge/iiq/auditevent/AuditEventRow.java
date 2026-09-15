package com.keyforge.iiq.auditevent;

import java.time.LocalDateTime;

/**
 * A row of {@code kf_audit_event} (IdentityIQ AuditEvent, audit-search level).
 *
 * <p>{@code action}, {@code source} and {@code target} are the raw IIQ strings, preserved
 * exactly ({@code target} is genuinely blank on some system events → NULL). {@code createdDisplay}
 * is the source's {@code created} value verbatim (a "MMMM d, yyyy, h:mm a" display string, rendered
 * in the session's timezone — minute precision, no seconds/timezone). {@code createdAt} is the
 * best-effort parse of that string into a naive wall-clock {@link LocalDateTime}; it is {@code null}
 * when the string does not match the one verified pattern (never fabricated). No action taxonomy is
 * derived here — {@code action_normalized} is intentionally not materialised until the KeyForge PDF
 * mapping is verified (Phase 6).
 */
public record AuditEventRow(
        String auditid,
        String action,
        String source,
        String target,
        String createdDisplay,
        LocalDateTime createdAt) {
}
