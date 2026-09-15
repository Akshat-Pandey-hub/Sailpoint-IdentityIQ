package com.keyforge.iiq.application;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code application} migration table, shaped by the fields
 * the IdentityIQ Application resource actually returns. No ISPM columns/defaults.
 *
 * @param applicationid    SCIM {@code id} (canonical UUID) — primary key
 * @param name             {@code name}
 * @param type             {@code type} (connector type, kept as the raw source string)
 * @param ownerId          {@code owner.value} as a canonical UUID, or null
 * @param ownerDisplayName {@code owner.displayName}
 * @param schemasJson      {@code applicationSchemas} array as JSON text, or null
 * @param featuresJson     {@code features} array as JSON text, or null
 * @param descriptionsJson {@code descriptions} array as JSON text, or null
 * @param createdAt        {@code meta.created}
 * @param modifiedAt       {@code meta.lastModified}
 */
public record ApplicationRow(
        String applicationid,
        String name,
        String type,
        String ownerId,
        String ownerDisplayName,
        String schemasJson,
        String featuresJson,
        String descriptionsJson,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
