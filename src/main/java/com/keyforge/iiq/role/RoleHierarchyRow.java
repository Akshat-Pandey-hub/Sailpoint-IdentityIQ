package com.keyforge.iiq.role;

/**
 * A row of the project-owned {@code kf_role_hierarchy} migration table: one role→role
 * edge derived from the SCIM Role resource's {@code inheritance} / {@code requirements} /
 * {@code permits} arrays.
 *
 * @param hierarchyid            deterministic UUID of ({@code role_id}|{@code edge_type}|{@code related_role_id})
 * @param roleId                 the role that owns the edge (canonical UUID)
 * @param roleName               the owning role's displayable name
 * @param relatedRoleId          the referenced role (canonical UUID), or null if unresolvable
 * @param relatedRoleDisplayName the referenced role's display name
 * @param edgeType               {@code inherits} | {@code requires} | {@code permits}
 */
public record RoleHierarchyRow(
        String hierarchyid,
        String roleId,
        String roleName,
        String relatedRoleId,
        String relatedRoleDisplayName,
        String edgeType) {
}
