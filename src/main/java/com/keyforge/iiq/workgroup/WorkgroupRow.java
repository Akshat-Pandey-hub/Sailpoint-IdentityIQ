package com.keyforge.iiq.workgroup;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code kf_workgroup} migration table (the PDF's
 * {@code kf_workgroup} target: governance workgroups = IdentityIQ Identities with
 * {@code workgroup=true}).
 *
 * <p>Sourced from the existing Group Configuration Workgroup DataSource via the unchanged
 * {@link com.keyforge.iiq.usergroup.UserGroupService}. On the current instance that source
 * only provides {@code id}, {@code name}, {@code description}, {@code modified}; the other
 * columns are populated from the same {@link com.keyforge.iiq.model.UserGroup} fields when
 * present and are otherwise NULL — never invented.
 *
 * @param workgroupid      IdentityIQ workgroup id (canonical UUID) — primary key
 * @param sourceId         raw IdentityIQ id, preserved verbatim
 * @param name             {@code name}
 * @param description      {@code description}
 * @param ownerId          {@code owner.value} as a canonical UUID, or null (not in current source)
 * @param ownerDisplayName {@code owner.display}, or null (not in current source)
 * @param status           {@code status}, or null (not in current source)
 * @param createdAt        {@code meta.created}, or null (not in current source)
 * @param modifiedAt       {@code modified}
 * @param memberCount      member count when the source carried a members array, else null
 */
public record WorkgroupRow(
        String workgroupid,
        String sourceId,
        String name,
        String description,
        String ownerId,
        String ownerDisplayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        Integer memberCount) {
}
