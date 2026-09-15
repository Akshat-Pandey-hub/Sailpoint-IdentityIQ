package com.keyforge.iiq.role;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code kf_role} migration table, shaped by the verified
 * IdentityIQ SCIM Role resource. Every value comes from a real source field; there is
 * no role→entitlement column because the SCIM Role resource does not expose one.
 *
 * @param roleid            SCIM {@code id} — primary key (canonical UUID)
 * @param name              {@code name}
 * @param displayableName   {@code displayableName}
 * @param roleType          {@code type.name} (e.g. business)
 * @param roleTypeDisplay   {@code type.displayName} (e.g. Business)
 * @param enabled           {@code active}
 * @param ownerId           {@code owner.value} as a canonical UUID, or null
 * @param ownerDisplayName  {@code owner.displayName}
 * @param descriptionsJson  {@code descriptions} array as JSON text, or null
 * @param classificationsJson {@code classifications} array as JSON text, or null
 * @param activationDate    {@code activationDate}
 * @param deactivationDate  {@code deactivationDate}
 * @param createdAt         {@code meta.created}
 * @param modifiedAt        {@code meta.lastModified}
 */
public record RoleRow(
        String roleid,
        String name,
        String displayableName,
        String roleType,
        String roleTypeDisplay,
        Boolean enabled,
        String ownerId,
        String ownerDisplayName,
        String descriptionsJson,
        String classificationsJson,
        LocalDateTime activationDate,
        LocalDateTime deactivationDate,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
