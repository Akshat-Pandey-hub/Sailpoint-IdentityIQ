package com.keyforge.iiq.application;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code applicationinstance} migration table. IdentityIQ
 * models one Application as one instance, so this table carries the instance identity
 * and its relationships (to {@code application} and to the owning {@code usr}). The
 * bulky descriptive data (schemas/features/descriptions) lives on the {@code application}
 * table, not duplicated here. No ISPM columns, no {@code configuration} column.
 *
 * @param instanceid       canonical UUID (= the Application id) — primary key
 * @param applicationid    FK to {@code application} (present only when that row exists)
 * @param instancename     Application {@code name}
 * @param appType          Application {@code type} (connector type)
 * @param ownerId          owner resolved to a {@code usr} userid, or null
 * @param ownerDisplayName {@code owner.displayName}
 * @param createdAt        {@code meta.created}
 * @param modifiedAt       {@code meta.lastModified}
 */
public record ApplicationInstanceRow(
        String instanceid,
        String applicationid,
        String instancename,
        String appType,
        String ownerId,
        String ownerDisplayName,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
