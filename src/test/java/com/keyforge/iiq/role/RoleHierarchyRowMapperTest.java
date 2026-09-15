package com.keyforge.iiq.role;

import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies role→role edge derivation for {@code kf_role_hierarchy}: inheritance/
 * requirements/permits become typed edges with canonical ids; empty arrays yield zero
 * rows (the expected outcome on the current instance); refs with no usable id are skipped.
 */
class RoleHierarchyRowMapperTest {

    private static Role role(String id, List<Role.Ref> inh, List<Role.Ref> req, List<Role.Ref> perm) {
        return new Role(id, "R", "R", null, null, null, null, null, null, null, null, inh, req, perm);
    }

    @Test
    void derivesTypedEdgesFromAllThreeArrays() {
        Role r = role("7f0001019fbf1a17819fc7e670310ee7",
                List.of(new Role.Ref("7f0001019fbf1a17819fc7e6276a0ee5", null, "HR Employee")),
                List.of(),
                List.of(new Role.Ref("7f0001019fa71124819fb63337a51c28", null, "Engineering-Base")));

        List<RoleHierarchyRow> rows = RoleHierarchyRowMapper.mapAll(r);
        assertEquals(2, rows.size());

        RoleHierarchyRow inh = rows.stream().filter(x -> x.edgeType().equals("inherits")).findFirst().orElseThrow();
        assertEquals("7f000101-9fbf-1a17-819f-c7e670310ee7", inh.roleId());
        assertEquals("7f000101-9fbf-1a17-819f-c7e6276a0ee5", inh.relatedRoleId());
        assertEquals("HR Employee", inh.relatedRoleDisplayName());

        RoleHierarchyRow perm = rows.stream().filter(x -> x.edgeType().equals("permits")).findFirst().orElseThrow();
        assertEquals("7f000101-9fa7-1124-819f-b63337a51c28", perm.relatedRoleId());

        // Deterministic id is stable and identical on a re-run.
        assertEquals(inh.hierarchyid(), RoleHierarchyRowMapper.mapAll(r).get(0).hierarchyid());
    }

    @Test
    void emptyArraysYieldZeroEdges() {
        List<RoleHierarchyRow> rows = RoleHierarchyRowMapper.mapAll(
                role("7f0001019fa71124819fb63337a51c28", List.of(), List.of(), List.of()));
        assertTrue(rows.isEmpty());
    }

    @Test
    void refWithoutUsableIdIsSkippedNotFabricated() {
        Role r = role("7f0001019fa71124819fb63337a51c28",
                List.of(new Role.Ref(null, null, "NoIdRole"), new Role.Ref("not-a-uuid", null, "BadId")),
                List.of(), List.of());
        assertTrue(RoleHierarchyRowMapper.mapAll(r).isEmpty());
    }
}
