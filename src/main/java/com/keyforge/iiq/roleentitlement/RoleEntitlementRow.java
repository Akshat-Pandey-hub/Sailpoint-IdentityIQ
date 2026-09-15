package com.keyforge.iiq.roleentitlement;

/**
 * A row of the project-owned {@code kf_role_entitlement} migration table — the PDF's
 * "Bundle profiles → role N-to-N entitlement (what the role grants per application)".
 *
 * <p>Sourced from the authoritative Role modeler direct-entitlements grid. The source
 * provides application/property/value/displayValue; {@code entitlement_id} is resolved
 * against the already-extracted entitlement catalog by (application, attribute, value) and
 * is NULL when the source's grant has no matching catalog entitlement (never fabricated).
 *
 * @param id             deterministic UUID of (role_id|application_name|property|value)
 * @param roleId         the granting role (canonical UUID)
 * @param roleName       the granting role's display name
 * @param applicationName source {@code applicationName}
 * @param property       source {@code property} (account attribute)
 * @param value          source {@code value} (entitlement value)
 * @param displayValue   source {@code displayValue}
 * @param classifications source {@code classifications}
 * @param entitlementId  catalog entitlement id resolved by (app, attribute, value), or null
 */
public record RoleEntitlementRow(
        String id,
        String roleId,
        String roleName,
        String applicationName,
        String property,
        String value,
        String displayValue,
        String classifications,
        String entitlementId) {
}
