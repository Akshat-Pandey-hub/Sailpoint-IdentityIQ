package com.keyforge.iiq.roleentitlement;

import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the role→entitlement mapping: source fields mapped, role id canonicalised,
 * entitlement_id resolved against the catalog by (application, attribute, value), left NULL
 * when no catalog match exists (never fabricated), and a deterministic stable PK.
 */
class RoleEntitlementRowMapperTest {

    private static final String ENT_ID = "7f0001019fbf1a17819fc906c0951106";

    private static Map<String, String> catalog() {
        Entitlement e = new Entitlement(ENT_ID, "Group One", "grp-1", "memberOf", Boolean.TRUE, "group",
                new Entitlement.ApplicationRef("EntraAuth", "7f00010198421229819849c815b90bfc", null));
        return RoleEntitlementRowMapper.buildCatalogIndex(List.of(e));
    }

    @Test
    void mapsGrantAndResolvesEntitlementId() {
        RoleEntitlementGrant g = new RoleEntitlementGrant(
                "7f0001019fa71124819fb63337a51c28", "EntraAuth", "memberOf", "grp-1", "Group One", null);

        RoleEntitlementRow row = RoleEntitlementRowMapper.map(g, "Engineering-Base", catalog());
        assertEquals("7f000101-9fa7-1124-819f-b63337a51c28", row.roleId());
        assertEquals("Engineering-Base", row.roleName());
        assertEquals("EntraAuth", row.applicationName());
        assertEquals("memberOf", row.property());
        assertEquals("grp-1", row.value());
        assertEquals("7f000101-9fbf-1a17-819f-c906c0951106", row.entitlementId());
        assertEquals(row.id(), RoleEntitlementRowMapper.map(g, "Engineering-Base", catalog()).id());
    }

    @Test
    void unmatchedGrantLeavesEntitlementIdNull() {
        RoleEntitlementGrant g = new RoleEntitlementGrant(
                "7f0001019fa71124819fb63337a51c28", "EntraAuth", "memberOf", "no-such-value", null, null);
        RoleEntitlementRow row = RoleEntitlementRowMapper.map(g, "Engineering-Base", catalog());
        assertNull(row.entitlementId());
        assertEquals("no-such-value", row.value());
    }

    @Test
    void nullCatalogYieldsNullEntitlementId() {
        RoleEntitlementGrant g = new RoleEntitlementGrant(
                "7f0001019fa71124819fb63337a51c28", "EntraAuth", "memberOf", "grp-1", null, null);
        assertNull(RoleEntitlementRowMapper.map(g, "R", null).entitlementId());
    }
}
