package com.keyforge.iiq.usergroup;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code usergroup} migration table. Every field the
 * IdentityIQ Group Configuration grid returns has its own column, so there is no
 * {@code customattributes} column — nothing would go in it but duplicates.
 *
 * @param id           deterministic UUID (from source type + source id/name) — primary key
 * @param sourceType   "Workgroup" / "Population" / "Group" (from the endpoint)
 * @param sourceId     IdentityIQ object id as returned by the source, or null
 * @param name         group name, or null
 * @param description  source description, or null
 * @param owner        owner as the source reports it, or null
 * @param status       source status, or null
 * @param createdAt    source created timestamp, or null
 * @param modifiedAt   source modified timestamp, or null
 * @param memberCount  number of members when the source lists them, else null
 */
public record UserGroupRow(
        String id,
        String sourceType,
        String sourceId,
        String name,
        String description,
        String owner,
        String status,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        Integer memberCount) {
}
