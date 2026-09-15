package com.keyforge.iiq.identityentitlement;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code kf_identity_entitlement} migration table — the PDF's
 * identity N-to-N entitlement relationship.
 *
 * <p><b>Not authoritative IIQ {@code IdentityEntitlement} provenance.</b> The relationship
 * itself is genuinely present in our existing extracted data (it is a projection of the
 * {@link com.keyforge.iiq.model.AccountEntitlementAssignment} derivation — identity → account
 * → entitlement). But the authoritative provenance the PDF lists — {@code source}
 * (role-derived/direct/detected/requested), {@code assigner}, assignment dates,
 * {@code aggregation_state}, {@code granted_by_role} — is NOT provided by the current source
 * and is therefore always NULL here (never fabricated). Those fields would require the
 * authoritative IIQ {@code IdentityEntitlement} object (JDBC/plugin), a separate future task.
 *
 * @param id                 deterministic UUID of (identity_id|application_id|source_attribute|entitlement_value)
 * @param identityId         the identity (canonical UUID)
 * @param identityDisplayName the identity's display name
 * @param applicationId      owning application id (canonical UUID), or null
 * @param applicationName    application display name
 * @param entitlementId      resolved entitlement id (canonical UUID), or null when unresolved
 * @param entitlementValue   the native entitlement value
 * @param entitlementType    the entitlement type, when resolved
 * @param sourceAttribute    the account attribute the value came from (e.g. "groups") — real
 * @param resolutionStatus   RESOLVED | UNRESOLVED (our derivation's resolution, not IIQ's)
 * @param source             IIQ grant source — NULL (not in current source)
 * @param assigner           IIQ assigner — NULL (not in current source)
 * @param assignedDate       assignment start — NULL (not in current source)
 * @param endDate            assignment end/expiration — NULL (not in current source)
 * @param aggregationState   IIQ aggregation state — NULL (not in current source)
 * @param grantedByRole      granting role — NULL (not in current source)
 */
public record IdentityEntitlementRow(
        String id,
        String identityId,
        String identityDisplayName,
        String applicationId,
        String applicationName,
        String entitlementId,
        String entitlementValue,
        String entitlementType,
        String sourceAttribute,
        String resolutionStatus,
        String source,
        String assigner,
        LocalDateTime assignedDate,
        LocalDateTime endDate,
        String aggregationState,
        String grantedByRole) {
}
