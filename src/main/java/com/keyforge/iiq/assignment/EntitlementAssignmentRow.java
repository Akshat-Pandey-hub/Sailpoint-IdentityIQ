package com.keyforge.iiq.assignment;

/**
 * A row of the project-owned {@code entitlementassignment} migration table. This is a
 * DERIVED relationship (account ↔ entitlement); the derivation provenance is stored in
 * proper typed columns, not a raw JSON dump.
 *
 * @param assignmentid         deterministic UUID (stable logical key) — primary key
 * @param accountid            FK to {@code account}, or null when unresolved
 * @param entitlementid        FK to {@code entitlement}, or null when unresolved
 * @param accountNativeName    the account's native name at derivation time
 * @param identityId           the correlated identity's id (canonical UUID), or null
 * @param identityDisplayName  the identity's display name
 * @param applicationId        the application's id (canonical UUID), or null
 * @param applicationName      the application's display name
 * @param entitlementValue     the entitlement value that produced this assignment
 * @param entitlementType      the entitlement type
 * @param sourceAttribute      the account attribute the value came from (e.g. groups)
 * @param resolutionStatus     how the assignment was resolved (e.g. RESOLVED)
 * @param resolutionSourcesJson resolution sources as a JSON array, or null
 */
public record EntitlementAssignmentRow(
        String assignmentid,
        String accountid,
        String entitlementid,
        String accountNativeName,
        String identityId,
        String identityDisplayName,
        String applicationId,
        String applicationName,
        String entitlementValue,
        String entitlementType,
        String sourceAttribute,
        String resolutionStatus,
        String resolutionSourcesJson) {
}
