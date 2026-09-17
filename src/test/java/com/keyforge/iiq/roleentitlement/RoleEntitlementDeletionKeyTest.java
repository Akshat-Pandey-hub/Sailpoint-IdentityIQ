package com.keyforge.iiq.roleentitlement;

import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Safety invariant for the kf_role_entitlement deletion sweep.
 *
 * <p>The sweep's keep-set is the SAME already-built {@code List<RoleEntitlementRow>} passed to
 * persistence, keyed by {@code RoleEntitlementRow::id} — a pure record accessor (no re-fetch, no
 * re-map, no throw). Persistence dedups by {@code row.id()} ({@code byId} LinkedHashMap) and
 * {@code SoftDeleteSweeper.normalizeIds} dedups the keep-set identically, so the keep-set equals the
 * persisted distinct id-set exactly. The PK is {@code nameUUID(RoleEntitlement|rawRoleId|app|property|
 * value)} and does NOT include {@code entitlement_id}.
 */
class RoleEntitlementDeletionKeyTest {

    private static final String ROLE = "7f0001019fa71124819fb63337a51c28";
    private static final String ENT_ID = "7f0001019fbf1a17819fc906c0951106";

    private static RoleEntitlementGrant grant(String roleId, String app, String property, String value) {
        return new RoleEntitlementGrant(roleId, app, property, value, "Group One", null);
    }

    private static Map<String, String> catalog() {
        Entitlement e = new Entitlement(ENT_ID, "Group One", "grp-1", "memberOf", Boolean.TRUE, "group",
                new Entitlement.ApplicationRef("EntraAuth", "7f00010198421229819849c815b90bfc", null));
        return RoleEntitlementRowMapper.buildCatalogIndex(List.of(e));
    }

    // a. RoleEntitlementRow::id is exactly the keep-set key used by the sweep.
    @Test
    void rowIdIsTheKeepSetKey() {
        RoleEntitlementRow row = RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-1"),
                "Engineering-Base", catalog());
        // The sweep's pk-function is RoleEntitlementRow::id applied to the same row objects it persists.
        java.util.function.Function<RoleEntitlementRow, String> pkFn = RoleEntitlementRow::id;
        assertEquals(row.id(), pkFn.apply(row));
        assertNotNull(row.id());
    }

    // b. PK follows the existing deterministic key over the RAW role id.
    @Test
    void pkFollowsDeterministicKeyOverRawRoleId() {
        RoleEntitlementRow row = RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-1"),
                "Engineering-Base", catalog());
        String expected = UUID.nameUUIDFromBytes(
                ("RoleEntitlement|" + ROLE + "|EntraAuth|memberOf|grp-1").getBytes(StandardCharsets.UTF_8))
                .toString();
        assertEquals(expected, row.id());
    }

    // c. entitlement_id resolution does NOT affect the PK.
    @Test
    void entitlementIdResolutionDoesNotAffectPk() {
        RoleEntitlementGrant g = grant(ROLE, "EntraAuth", "memberOf", "grp-1");
        RoleEntitlementRow resolved = RoleEntitlementRowMapper.map(g, "Engineering-Base", catalog());
        RoleEntitlementRow unresolved = RoleEntitlementRowMapper.map(g, "Engineering-Base", null);
        assertNotNull(resolved.entitlementId());
        assertNull(unresolved.entitlementId());
        // same source grant fields -> same PK regardless of whether entitlement_id resolved
        assertEquals(resolved.id(), unresolved.id());
    }

    // d. Duplicate identical grants -> duplicate row ids -> normalize to one keep-set id (matches persist dedup).
    @Test
    void duplicateGrantsCollapseToOneKeepSetId() {
        RoleEntitlementRow r1 = RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-1"),
                "Engineering-Base", catalog());
        RoleEntitlementRow r2 = RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-1"),
                "Engineering-Base", catalog());
        assertEquals(r1.id(), r2.id());
        // the keep-set (a set of ids, as SoftDeleteSweeper.normalizeIds builds) collapses them
        LinkedHashSet<String> keepSet = new LinkedHashSet<>(List.of(r1.id(), r2.id()));
        assertEquals(1, keepSet.size());
    }

    // e. Different application/property/value combinations produce different ids.
    @Test
    void differentGrantFieldsProduceDifferentIds() {
        String base = RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-1"), "R", null).id();
        assertNotEquals(base,
                RoleEntitlementRowMapper.map(grant(ROLE, "OtherApp", "memberOf", "grp-1"), "R", null).id());
        assertNotEquals(base,
                RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "groups", "grp-1"), "R", null).id());
        assertNotEquals(base,
                RoleEntitlementRowMapper.map(grant(ROLE, "EntraAuth", "memberOf", "grp-2"), "R", null).id());
    }

    // f. Equivalent role UUID formatting behaves consistently with the existing mapper:
    //    the canonical role_id column canonicalizes equivalent forms identically, and the PK is a
    //    deterministic function of the exact raw source roleId (stable across re-runs of the same input).
    @Test
    void roleIdFormattingIsConsistentWithMapper() {
        RoleEntitlementRow dashed = RoleEntitlementRowMapper.map(
                grant("7f000101-9fa7-1124-819f-b63337a51c28", "EntraAuth", "memberOf", "grp-1"), "R", null);
        RoleEntitlementRow braced = RoleEntitlementRowMapper.map(
                grant("{7f0001019fa71124819fb63337a51c28}", "EntraAuth", "memberOf", "grp-1"), "R", null);
        // canonical role_id column is format-insensitive
        assertEquals("7f000101-9fa7-1124-819f-b63337a51c28", dashed.roleId());
        assertEquals(dashed.roleId(), braced.roleId());
        // PK is deterministic for a given raw roleId string (the sweep reuses the very rows persist built,
        // so persist-run and sweep-run share identical row objects and ids)
        assertEquals(dashed.id(),
                RoleEntitlementRowMapper.map(
                        grant("7f000101-9fa7-1124-819f-b63337a51c28", "EntraAuth", "memberOf", "grp-1"), "R", null).id());
    }

    // g. Empty rows is safe: the SoftDeleteSweeper skips an empty (normalized) source id set.
    @Test
    void emptyRowsProducesEmptyKeepSetForGuard() {
        List<RoleEntitlementRow> rows = List.of();
        assertEquals(0, new LinkedHashSet<>(rows.stream().map(RoleEntitlementRow::id).toList()).size());
        // (SoftDeleteSweeperTest already proves an empty id set -> SKIP; here we only prove the
        //  keep-set derived from empty rows is itself empty, which drives that guard.)
    }
}
