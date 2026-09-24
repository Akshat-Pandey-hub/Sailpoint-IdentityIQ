package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native identity-role edges. */
class NativeIdentityRoleRepositoryTest {

    private static NativeIdentityRoleRecord rec(String identityId, String roleId, String type, String assignmentId) {
        NativeIdentityRoleRecord r = new NativeIdentityRoleRecord();
        r.identityId = identityId;
        r.roleId = roleId;
        r.roleName = "Role-X";
        r.relationshipType = type;
        r.assignmentId = assignmentId;
        return r;
    }

    @Test
    void edgeIdIsDeterministicOverIdentityRoleTypeAssignment() {
        String a = NativeIdentityRoleRepository.canonicalEdgeId(rec("id1", "role1", "ASSIGNED", "asg1"));
        String b = NativeIdentityRoleRepository.canonicalEdgeId(rec("id1", "role1", "ASSIGNED", "asg1"));
        assertNotNull(a);
        assertEquals(a, b, "same edge ⇒ same id (never random)");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void assignedAndDetectedEdgesOfSameRoleAreDistinct() {
        String assigned = NativeIdentityRoleRepository.canonicalEdgeId(rec("id1", "role1", "ASSIGNED", "asg1"));
        String detected = NativeIdentityRoleRepository.canonicalEdgeId(rec("id1", "role1", "DETECTED", null));
        assertNotEquals(assigned, detected, "relationship_type disambiguates assigned vs detected");
    }

    @Test
    void recordHashChangesWhenRoleChanges() {
        String h1 = NativeIdentityRoleRepository.recordHash(rec("id1", "role1", "ASSIGNED", "asg1"));
        String h2 = NativeIdentityRoleRepository.recordHash(rec("id1", "role2", "ASSIGNED", "asg1"));
        assertNotNull(h1);
        assertNotEquals(h1, h2);
    }
}
