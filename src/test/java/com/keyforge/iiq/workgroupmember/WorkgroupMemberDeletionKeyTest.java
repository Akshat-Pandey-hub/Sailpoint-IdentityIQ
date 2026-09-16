package com.keyforge.iiq.workgroupmember;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Safety invariant for the kf_workgroup_member deletion sweep: the keep-set is built with
 * {@code WorkgroupMemberRowMapper.map(m).id()}, which must reproduce the exact stored PK
 * ({@code deterministicId(canonicalUuid(workgroupId), canonicalUuid(identityId))}). If it didn't,
 * the sweep could mark a live member deleted. Also locks that invalid/null ids throw (so the helper
 * excludes them and never deletes from an unmappable source record).
 */
class WorkgroupMemberDeletionKeyTest {

    private static final String WG = "7f00010198421229819849f9859c0e4a";
    private static final String ID = "7f00010198421229819849ebca370c93";

    private static WorkgroupMembership m(String wg, String id) {
        return new WorkgroupMembership(wg, id, "alice", "Alice", "Cook");
    }

    @Test
    void mapReproducesStoredDeterministicPk() {
        String pk = WorkgroupMemberRowMapper.map(m(WG, ID)).id();
        String expected = WorkgroupMemberRowMapper.deterministicId(
                WorkgroupMemberRowMapper.toCanonicalUuid(WG, "workgroup"),
                WorkgroupMemberRowMapper.toCanonicalUuid(ID, "identity"));
        assertEquals(expected, pk, "keep-set id must equal the stored kf_workgroup_member.id");
        // deterministic / stable across calls
        assertEquals(pk, WorkgroupMemberRowMapper.map(m(WG, ID)).id());
    }

    @Test
    void bracedAndDashedFormsProduceTheSamePk() {
        String dashedWg = "7f000101-9842-1229-8198-49f9859c0e4a";
        String bracedId = "{" + ID + "}";
        assertEquals(WorkgroupMemberRowMapper.map(m(WG, ID)).id(),
                WorkgroupMemberRowMapper.map(m(dashedWg, bracedId)).id());
    }

    @Test
    void invalidOrNullIdsThrow_soTheKeepSetSafelyExcludesThem() {
        assertThrows(WorkgroupMemberMappingException.class, () -> WorkgroupMemberRowMapper.map(m(null, ID)));
        assertThrows(WorkgroupMemberMappingException.class, () -> WorkgroupMemberRowMapper.map(m(WG, "")));
        assertThrows(WorkgroupMemberMappingException.class, () -> WorkgroupMemberRowMapper.map(m("not-a-uuid", ID)));
        // WorkgroupMemberMappingException is a RuntimeException, so Main.sweepDeletionsByPk's
        // catch(RuntimeException) drops such records from the keep-set (no accidental deletion).
    }
}
