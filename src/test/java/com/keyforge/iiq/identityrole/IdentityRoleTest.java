package com.keyforge.iiq.identityrole;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for Identity&rarr;Role extraction: parsing the real {@code rest/identities/{id}}
 * {@code assignedRoles[]}, mapping, canonical FK alignment, deterministic edge id, epoch→UTC, and
 * NULL handling for the assigner/description the source leaves empty.
 */
class IdentityRoleTest {

    private static final String IDENTITY_ID = "7f0001019f061fdc819f9cdef3bc0862";

    /** Verbatim shape from the live rest/identities/{id} response. */
    private static final String DETAIL = "{\"assignedRoles\":[{\"date\":1786845600405,"
            + "\"displayName\":\"IT Operations Employee\",\"assigner\":null,\"description\":null,"
            + "\"id\":\"7f0001019fbf1a17819fc7e670310ee7\"}],"
            + "\"viewableIdentityAttributes\":[],\"listAttributes\":{}}";

    private final IdentityRoleService service = new IdentityRoleService(null);

    @Test
    void parsesAssignedRolesWithIdentityTag() {
        List<IdentityRoleAssignment> rels = service.parseAssignments(IDENTITY_ID, DETAIL);
        assertEquals(1, rels.size());
        IdentityRoleAssignment a = rels.get(0);
        assertEquals(IDENTITY_ID, a.identityId());
        assertEquals("7f0001019fbf1a17819fc7e670310ee7", a.roleId());
        assertEquals("IT Operations Employee", a.roleDisplayName());
        assertEquals(1786845600405L, a.assignedDate());
        assertNull(a.assigner());       // source exposes the field but it is null across live data
        assertNull(a.description());
    }

    @Test
    void mapsToRowWithCanonicalFksAndUtcDate() {
        IdentityRoleAssignment a = service.parseAssignments(IDENTITY_ID, DETAIL).get(0);
        IdentityRoleRow row = IdentityRoleRowMapper.map(a);
        assertEquals("7f000101-9f06-1fdc-819f-9cdef3bc0862", row.identityid()); // FK -> kf_identity.userid
        assertEquals("7f000101-9fbf-1a17-819f-c7e670310ee7", row.roleid());     // FK -> kf_role.roleid
        assertEquals(IDENTITY_ID, row.sourceIdentityId());
        assertEquals("7f0001019fbf1a17819fc7e670310ee7", row.sourceRoleId());
        assertEquals("IT Operations Employee", row.roleDisplayName());
        assertEquals(Instant.ofEpochMilli(1786845600405L), row.assignedAt().toInstant(ZoneOffset.UTC));
        assertNull(row.assigner());
    }

    @Test
    void edgeIdIsDeterministicPerIdentityRolePair() {
        IdentityRoleAssignment a = service.parseAssignments(IDENTITY_ID, DETAIL).get(0);
        String id1 = IdentityRoleRowMapper.map(a).id();
        String id2 = IdentityRoleRowMapper.map(a).id();
        assertEquals(id1, id2); // idempotent upsert relies on a stable id for (identity, role)
        // a different role yields a different edge id
        IdentityRoleAssignment other = new IdentityRoleAssignment(IDENTITY_ID,
                "7f0001019fa71124819fb63337a51c28", "Other", 1L, null, null);
        assertTrue(!IdentityRoleRowMapper.map(other).id().equals(id1));
    }

    @Test
    void missingIdsAreRejectedNotFabricated() {
        assertThrows(IdentityRoleMappingException.class,
                () -> IdentityRoleRowMapper.map(new IdentityRoleAssignment(null, "7f00", "R", 1L, null, null)));
        assertThrows(IdentityRoleMappingException.class,
                () -> IdentityRoleRowMapper.map(new IdentityRoleAssignment(IDENTITY_ID, "  ", "R", 1L, null, null)));
    }

    @Test
    void emptyAssignedRolesYieldsNoRelationships() {
        assertTrue(service.parseAssignments(IDENTITY_ID,
                "{\"assignedRoles\":[],\"listAttributes\":{}}").isEmpty());
        assertTrue(service.parseAssignments(IDENTITY_ID, "{\"listAttributes\":{}}").isEmpty());
    }

    @Test
    void repositoryRespectsPgSchema() {
        IdentityRoleRepository repo = new IdentityRoleRepository("iiq_migration_final");
        assertEquals("iiq_migration_final", repo.schema());
        assertEquals("iiq_migration_final.kf_identity_role", repo.targetTable());
    }
}
