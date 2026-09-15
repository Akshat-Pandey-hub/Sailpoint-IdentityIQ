package com.keyforge.iiq.auditevent;

/**
 * A SailPoint IdentityIQ AuditEvent as returned by the Advanced-Analytics Audit Search
 * datasource ({@code /analyze/audit/auditDataSource.json}). This datasource exposes exactly
 * five fields (verified from its own {@code metaData}); nothing else is available here.
 *
 * @param id      the AuditEvent id (32-char GUID)
 * @param action  the raw IIQ action string (preserved exactly, e.g. "EntitlementAdd")
 * @param source  the actor (display name / task name), exactly as supplied
 * @param target  the object the event concerns (identity/entitlement name, typed
 *                "Identity:&lt;name&gt;", or a system string), exactly as supplied
 * @param created the created timestamp as a formatted display string
 *                ("MMMM d, yyyy, h:mm a" — minute precision, no seconds/timezone)
 */
public record AuditEvent(
        String id,
        String action,
        String source,
        String target,
        String created) {
}
