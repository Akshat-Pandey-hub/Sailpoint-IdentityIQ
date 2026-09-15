package com.keyforge.iiq.accountentitlement;

/**
 * A row of the project-owned {@code kf_account_entitlement} migration table — the PDF's
 * "account N-to-N entitlement as literally present on the account" (the detected-on-account
 * truth used for reconciliation).
 *
 * <p>This is a normalized <b>projection of already-extracted data</b>: it reuses the exact
 * {@link com.keyforge.iiq.model.AccountEntitlementAssignment} derivation that already
 * populates {@code entitlementassignment} (no new IdentityIQ source). Unresolved values are
 * kept with {@code entitlement_id} NULL (never dropped, never invented).
 *
 * @param id               deterministic UUID of (account_id|application_id|source_attribute|entitlement_value)
 * @param accountId        owning account id (canonical UUID), or null
 * @param accountNativeName the account's native identity/display name
 * @param applicationId    owning application id (canonical UUID), or null
 * @param applicationName  the application display name
 * @param entitlementId    resolved entitlement id (canonical UUID), or null when unresolved
 * @param entitlementValue the native value literally held on the account
 * @param entitlementType  the entitlement type, when resolved
 * @param sourceAttribute  the account attribute the value came from (e.g. "groups")
 * @param resolutionStatus RESOLVED | UNRESOLVED
 */
public record AccountEntitlementRow(
        String id,
        String accountId,
        String accountNativeName,
        String applicationId,
        String applicationName,
        String entitlementId,
        String entitlementValue,
        String entitlementType,
        String sourceAttribute,
        String resolutionStatus) {
}
