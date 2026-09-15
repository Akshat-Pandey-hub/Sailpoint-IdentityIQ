package com.keyforge.iiq.account;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code account} migration table, shaped by the IdentityIQ
 * SCIM Account resource. Core fields are typed columns; relationships are FK columns.
 * The {@code attributes} column holds ONLY the application-specific connector attribute
 * bag (mail, groups[], proxyAddresses[], …) that genuinely cannot have fixed columns —
 * it is not a dump of the whole resource and does not duplicate the mapped columns.
 *
 * @param accountid              SCIM {@code id} — primary key
 * @param userid                 {@code identity.value} resolved to a {@code usr}, or null
 * @param identityDisplayName    {@code identity.displayName}
 * @param instanceid             {@code application.value} resolved to an instance, or null
 * @param applicationDisplayName {@code application.displayName}
 * @param nativeIdentity         {@code nativeIdentity} (native account key)
 * @param accountDisplayName     account {@code displayName}
 * @param active                 {@code active}
 * @param locked                 {@code locked}
 * @param hasEntitlements        {@code hasEntitlements}
 * @param manuallyCorrelated     {@code manuallyCorrelated}
 * @param lastRefresh            {@code lastRefresh}
 * @param createdAt              {@code meta.created}
 * @param modifiedAt             {@code meta.lastModified}
 * @param attributesJson         connector attribute object as JSON text, or null
 */
public record AccountRow(
        String accountid,
        String userid,
        String identityDisplayName,
        String instanceid,
        String applicationDisplayName,
        String nativeIdentity,
        String accountDisplayName,
        Boolean active,
        Boolean locked,
        Boolean hasEntitlements,
        Boolean manuallyCorrelated,
        LocalDateTime lastRefresh,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        String attributesJson) {
}
