package com.keyforge.iiq.workgroupmember;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the membership → {@code kf_workgroup_member} mapping: ids canonicalised, name parts
 * mapped, deterministic PK on (workgroup, identity), and missing ids rejected (never invented).
 */
class WorkgroupMemberRowMapperTest {

    private static WorkgroupMembership sample() {
        return new WorkgroupMembership(
                "7f0001019f061fdc819f10581517110e", "7f000101971416688197147684ad00ff",
                "spadmin", "Molly", "J");
    }

    @Test
    void mapsCanonicalIdsAndNameParts() {
        WorkgroupMemberRow row = WorkgroupMemberRowMapper.map(sample());
        assertEquals("7f000101-9f06-1fdc-819f-10581517110e", row.workgroupId());
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.identityId());
        assertEquals("spadmin", row.memberName());
        assertEquals("Molly", row.firstName());
        assertEquals("J", row.lastName());
        // Deterministic PK is stable across runs.
        assertEquals(row.id(), WorkgroupMemberRowMapper.map(sample()).id());
    }

    @Test
    void missingIdentityIdIsRejected() {
        WorkgroupMembership noIdentity =
                new WorkgroupMembership("7f0001019f061fdc819f10581517110e", null, "x", null, null);
        assertThrows(WorkgroupMemberMappingException.class, () -> WorkgroupMemberRowMapper.map(noIdentity));
    }

    @Test
    void missingWorkgroupIdIsRejected() {
        WorkgroupMembership noWg =
                new WorkgroupMembership(null, "7f000101971416688197147684ad00ff", "x", null, null);
        assertThrows(WorkgroupMemberMappingException.class, () -> WorkgroupMemberRowMapper.map(noWg));
    }
}
