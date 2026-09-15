package com.keyforge.iiq.entitlement;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code entitlement} migration table, shaped by the
 * IdentityIQ SCIM Entitlement resource. {@code type} is the raw source string (text,
 * not an ISPM enum); {@code requestable}/{@code aggregated} are real boolean columns.
 *
 * @param entitlementid          SCIM {@code id} — primary key
 * @param value                  {@code value}
 * @param displayableName        {@code displayableName}
 * @param type                   {@code type} (raw source string, e.g. group / posixgroup)
 * @param attribute              {@code attribute} (e.g. groups / posixgroups)
 * @param aggregated             {@code aggregated}
 * @param requestable            {@code requestable}
 * @param instanceid             {@code application.value} resolved to an instance, or null
 * @param applicationDisplayName {@code application.displayName}
 * @param lastRefresh            {@code lastRefresh}
 * @param createdAt              {@code meta.created}
 * @param modifiedAt             {@code meta.lastModified}
 * @param schemasJson            {@code schemas} array as JSON text, or null
 */
public record EntitlementRow(
        String entitlementid,
        String value,
        String displayableName,
        String type,
        String attribute,
        Boolean aggregated,
        Boolean requestable,
        String instanceid,
        String applicationDisplayName,
        LocalDateTime lastRefresh,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        String schemasJson) {
}
