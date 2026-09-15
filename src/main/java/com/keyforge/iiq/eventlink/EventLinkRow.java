package com.keyforge.iiq.eventlink;

/**
 * A row of the derived {@code kf_event_link} relationship table — the audit-event → object side.
 *
 * <p>Every row preserves the authoritative {@code auditEventId} and the verbatim {@code targetRaw}
 * so no reference is ever lost. {@code targetObjectType}/{@code targetObjectId} are populated only
 * when the target resolved to exactly one existing Identity/Account/Entitlement; otherwise they are
 * NULL and {@code linkStatus} records why (UNRESOLVED / AMBIGUOUS / OUT_OF_SCOPE_TYPE).
 *
 * @param id               deterministic UUID of (audit event | audit-target link) — 1 row/event, rerun-stable
 * @param auditEventId     the source AuditEvent id (canonical UUID), preserved on every record
 * @param sourceObjectType always {@code "AuditEvent"} (the derivation source)
 * @param targetObjectType resolved entity type (Identity/Account/Entitlement), or null
 * @param targetObjectId   resolved object id (canonical UUID), or null when not resolved
 * @param targetRaw        the exact raw audit {@code target} string (preserved, never invented)
 * @param targetTypeHint   the parsed {@code Type:} prefix when present (e.g. Identity, Application), else null
 * @param linkStatus       RESOLVED | UNRESOLVED | AMBIGUOUS | OUT_OF_SCOPE_TYPE
 * @param resolutionRule   short description of how resolution was decided
 */
public record EventLinkRow(
        String id,
        String auditEventId,
        String sourceObjectType,
        String targetObjectType,
        String targetObjectId,
        String targetRaw,
        String targetTypeHint,
        String linkStatus,
        String resolutionRule) {
}
