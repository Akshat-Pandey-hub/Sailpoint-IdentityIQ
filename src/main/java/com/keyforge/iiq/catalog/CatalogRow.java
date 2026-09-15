package com.keyforge.iiq.catalog;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code catalog} migration table, DERIVED from requestable
 * entitlements. Only fields that genuinely originate from IdentityIQ (or from the
 * documented derivation) are present — no ISPM columns, no ISPM defaults (no tenant_id,
 * flags, etc.).
 *
 * @param catalogid             deterministic UUID from (name, appinstanceid) — primary key
 * @param name                  catalog item name (entitlement displayableName/value)
 * @param type                  always {@code Entitlement}
 * @param entitlementid         FK to the primary {@code entitlement}, or null
 * @param entitlementName       primary entitlement {@code value}
 * @param entitlementType       primary entitlement raw {@code type}
 * @param requestable           primary entitlement {@code requestable}
 * @param applicationName       primary entitlement application display name
 * @param appinstanceid         FK to {@code applicationinstance}, or null
 * @param createdAt             primary entitlement {@code meta.created}
 * @param modifiedAt            primary entitlement {@code meta.lastModified}
 * @param sourceEntitlementsJson derivation provenance: which entitlement(s) produced this
 *                               catalog item, as JSON text (genuinely useful, not a dump)
 */
public record CatalogRow(
        String catalogid,
        String name,
        String type,
        String entitlementid,
        String entitlementName,
        String entitlementType,
        Boolean requestable,
        String applicationName,
        String appinstanceid,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        String sourceEntitlementsJson) {
}
