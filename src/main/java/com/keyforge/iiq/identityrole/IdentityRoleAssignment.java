package com.keyforge.iiq.identityrole;

/**
 * One authoritative Identity&rarr;Role assignment, as returned by the IdentityIQ classic REST endpoint
 * {@code GET /rest/identities/{id}} in its {@code assignedRoles[]} array (verified live). This is the
 * Identity object's own persisted {@code assignedRoles} relationship — NOT inferred from role
 * definitions, entitlements, accounts, or access requests.
 *
 * <p>Observed shape per assigned role:
 * <pre>{ "id":"7f0001...", "displayName":"IT Operations Employee", "date":1786845600405,
 *        "assigner":null, "description":null }</pre>
 *
 * @param identityId      the owning Identity id (raw 32-char IIQ GUID; the {id} that was queried)
 * @param roleId          the assigned role's id (raw 32-char IIQ GUID) — FK to kf_role
 * @param roleDisplayName the role's display name as the source presented it
 * @param assignedDate    assignment date as epoch milliseconds (UTC), or null
 * @param assigner        who assigned the role (source exposes the field; null across live data)
 * @param description     assignment description (source exposes the field; null across live data)
 */
public record IdentityRoleAssignment(
        String identityId,
        String roleId,
        String roleDisplayName,
        Long assignedDate,
        String assigner,
        String description) {
}
