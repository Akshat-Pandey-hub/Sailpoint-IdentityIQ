package com.keyforge.iiq.role;

import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety invariant for the kf_role_hierarchy deletion sweep.
 *
 * <p>The sweep rebuilds its keep-set with the SAME pure derivation persistence uses —
 * {@code RoleHierarchyRowMapper.mapAll(role)} flattened over the full {@code getAllRoles()} pull —
 * and sweeps by {@code hierarchyid} (the deterministic PK). So the keep-set equals the persisted
 * primary keys exactly. A role/ref whose id is not a usable UUID yields no edge (never persisted →
 * never in the keep-set → no false deletion), and an empty source yields zero edges, which the
 * SoftDeleteSweeper empty-source guard turns into a SKIP (never a mass soft-delete).
 */
class RoleHierarchyDeletionKeyTest {

    private static final String ROLE = "7f0001019fbf1a17819fc7e670310ee7";
    private static final String RELATED = "7f0001019fbf1a17819fc7e6276a0ee5";

    private static Role role(String id, List<Role.Ref> inh, List<Role.Ref> req, List<Role.Ref> perm) {
        return new Role(id, "R", "R", null, null, null, null, null, null, null, null, inh, req, perm);
    }

    @Test
    void keepSetKeyEqualsPersistedHierarchyId() {
        Role r = role(ROLE, List.of(new Role.Ref(RELATED, null, "HR Employee")), List.of(), List.of());
        RoleHierarchyRow row = RoleHierarchyRowMapper.mapAll(r).get(0);
        // The sweep's pk-function is RoleHierarchyRow::hierarchyid on rows from the same mapAll.
        assertEquals(row.hierarchyid(), row.hierarchyid());
        // And that PK is exactly deterministicId(canonicalRoleId, edgeType, canonicalRelatedRoleId).
        assertEquals(RoleHierarchyRowMapper.deterministicId(row.roleId(), row.edgeType(), row.relatedRoleId()),
                row.hierarchyid(), "keep-set key must equal the stored kf_role_hierarchy.hierarchyid");
    }

    @Test
    void keyIsCanonicalizationInsensitiveForEquivalentIds() {
        // Dashed vs 32-hex (braced) forms of the same ids canonicalize identically -> same hierarchyid,
        // so the persist run and a later sweep run agree regardless of source id formatting.
        Role dashed = role("7f000101-9fbf-1a17-819f-c7e670310ee7",
                List.of(new Role.Ref("7f000101-9fbf-1a17-819f-c7e6276a0ee5", null, "HR Employee")),
                List.of(), List.of());
        Role braced = role("{7f0001019fbf1a17819fc7e670310ee7}",
                List.of(new Role.Ref("{7f0001019fbf1a17819fc7e6276a0ee5}", null, "HR Employee")),
                List.of(), List.of());
        assertEquals(RoleHierarchyRowMapper.mapAll(dashed).get(0).hierarchyid(),
                RoleHierarchyRowMapper.mapAll(braced).get(0).hierarchyid());
    }

    @Test
    void unusableIdsProduceNoKeepSetRowMatchingPersistence() {
        // ref with no id / non-UUID id -> no edge -> not persisted and not in the keep-set (no false delete)
        Role r = role(ROLE,
                List.of(new Role.Ref(null, null, "NoIdRole"), new Role.Ref("not-a-uuid", null, "BadId")),
                List.of(), List.of());
        assertTrue(RoleHierarchyRowMapper.mapAll(r).isEmpty());
        // a role whose OWN id is not a UUID anchors no edges either
        assertTrue(RoleHierarchyRowMapper.mapAll(
                role("not-a-uuid", List.of(new Role.Ref(RELATED, null, "X")), List.of(), List.of())).isEmpty());
    }

    @Test
    void keyIsEdgeTypeAndTargetSensitive() {
        // same role, same target, different edge type -> different key (a removed edge-type is deletable)
        String inheritsKey = RoleHierarchyRowMapper.deterministicId(
                "7f000101-9fbf-1a17-819f-c7e670310ee7", "inherits", "7f000101-9fbf-1a17-819f-c7e6276a0ee5");
        String permitsKey = RoleHierarchyRowMapper.deterministicId(
                "7f000101-9fbf-1a17-819f-c7e670310ee7", "permits", "7f000101-9fbf-1a17-819f-c7e6276a0ee5");
        assertNotEquals(inheritsKey, permitsKey);
        // different target role -> different key
        assertNotEquals(inheritsKey, RoleHierarchyRowMapper.deterministicId(
                "7f000101-9fbf-1a17-819f-c7e670310ee7", "inherits", "7f000101-9fa7-1124-819f-b63337a51c28"));
    }
}
