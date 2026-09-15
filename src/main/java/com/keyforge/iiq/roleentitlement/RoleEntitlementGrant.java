package com.keyforge.iiq.roleentitlement;

/**
 * One direct entitlement a Role/Bundle grants through its Profiles, as returned by the
 * IdentityIQ Role modeler "Direct Entitlements" grid
 * ({@code define/roles/modeler/readOnlySimpleEntitlementsJSON.json}). IIQ has already
 * resolved the profile constraints/filters into concrete (application, property, value)
 * entitlements, so this carries exactly what the authoritative source returns.
 *
 * @param roleId          the granting role's IdentityIQ id (the {@code roleId} we queried)
 * @param applicationName source {@code applicationName}
 * @param property        source {@code property} (the account attribute, e.g. "memberOf")
 * @param value           source {@code value} (the entitlement value)
 * @param displayValue    source {@code displayValue}
 * @param classifications source {@code classifications}
 */
public record RoleEntitlementGrant(
        String roleId,
        String applicationName,
        String property,
        String value,
        String displayValue,
        String classifications) {
}
