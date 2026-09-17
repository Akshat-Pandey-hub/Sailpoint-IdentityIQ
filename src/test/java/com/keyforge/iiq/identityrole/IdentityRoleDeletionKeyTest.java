package com.keyforge.iiq.identityrole;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Safety invariant for the kf_identity_role deletion sweep.
 *
 * <p>The sweep's keep-set pk-function is {@code IdentityRoleRowMapper.map(a).id()} — the exact mapper
 * persistence uses — over the SAME {@code rels} list that was persisted. So every persisted edge's
 * {@code id} is reproduced in the keep-set. An assignment whose identity or role id is missing/invalid
 * throws {@link IdentityRoleMappingException} (a {@code RuntimeException}); {@code sweepDeletionsByPk}
 * catches it and excludes the record — exactly as {@code persistOne} skips it (never persisted), so no
 * persisted row is ever omitted from the keep-set (which would be a false deletion).
 */
class IdentityRoleDeletionKeyTest {

    private static final String IDENTITY = "7f0001019f061fdc819f9cdef3bc0862";
    private static final String ROLE = "7f0001019fbf1a17819fc7e670310ee7";

    private static IdentityRoleAssignment a(String identityId, String roleId) {
        return new IdentityRoleAssignment(identityId, roleId, "R", 1L, null, null);
    }

    /** The exact pk-function the sweep uses (see runExtractIdentityRolesDb). */
    private static String keepSetPk(IdentityRoleAssignment as) {
        return IdentityRoleRowMapper.map(as).id();
    }

    @Test
    void keepSetKeyEqualsPersistedRowId() {
        IdentityRoleAssignment as = a(IDENTITY, ROLE);
        IdentityRoleRow persisted = IdentityRoleRowMapper.map(as);
        assertEquals(persisted.id(), keepSetPk(as), "keep-set key must equal the stored kf_identity_role.id");
        // and it is exactly nameUUID(canonicalIdentity|canonicalRole)
        assertEquals(
                java.util.UUID.nameUUIDFromBytes(
                        (persisted.identityid() + "|" + persisted.roleid())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                persisted.id());
    }

    @Test
    void equivalentUuidFormatsProduceTheSameKey() {
        // dashed, 32-hex, and braced forms of the same identity/role ids canonicalize identically,
        // so the persist run and a later sweep run agree regardless of source id formatting.
        String dashed = keepSetPk(a("7f000101-9f06-1fdc-819f-9cdef3bc0862",
                "7f000101-9fbf-1a17-819f-c7e670310ee7"));
        String hex = keepSetPk(a("7f0001019f061fdc819f9cdef3bc0862",
                "7f0001019fbf1a17819fc7e670310ee7"));
        String braced = keepSetPk(a("{7f0001019f061fdc819f9cdef3bc0862}",
                "{7f0001019fbf1a17819fc7e670310ee7}"));
        assertEquals(dashed, hex);
        assertEquals(hex, braced);
    }

    @Test
    void invalidOrMissingIdsAreExcludedConsistentlyWithPersistence() {
        // map() throws IdentityRoleMappingException (RuntimeException) -> persistOne skips (never persisted)
        // and sweepDeletionsByPk catches+excludes -> the record is in neither set. No false deletion.
        assertThrows(IdentityRoleMappingException.class, () -> keepSetPk(a(null, ROLE)));
        assertThrows(IdentityRoleMappingException.class, () -> keepSetPk(a(IDENTITY, "  ")));
        assertThrows(IdentityRoleMappingException.class, () -> keepSetPk(a(IDENTITY, "not-a-uuid")));
    }

    @Test
    void keyIsDeterministicAndPairSensitive() {
        assertEquals(keepSetPk(a(IDENTITY, ROLE)), keepSetPk(a(IDENTITY, ROLE))); // persist run == sweep run
        // a different role for the same identity is a different edge
        assertNotEquals(keepSetPk(a(IDENTITY, ROLE)),
                keepSetPk(a(IDENTITY, "7f0001019fa71124819fb63337a51c28")));
        // a different identity holding the same role is a different edge
        assertNotEquals(keepSetPk(a(IDENTITY, ROLE)),
                keepSetPk(a("7f0001019fa71124819fb63337a51c28", ROLE)));
        assertNotNull(keepSetPk(a(IDENTITY, ROLE)));
    }
}
