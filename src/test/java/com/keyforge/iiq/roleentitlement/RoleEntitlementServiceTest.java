package com.keyforge.iiq.roleentitlement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the pure parsing of the Role modeler direct-entitlements response, using the
 * verified {@code SimpleEntitlement} field names ({@code applicationName/property/value/
 * displayValue/classifications}) and the {@code entitlements}/{@code numEntitlements} envelope.
 */
class RoleEntitlementServiceTest {

    private static final String JSON =
            "{\"entitlements\":[{\"applicationName\":\"EntraAuth\",\"roleName\":\"Engineering-Base\","
            + "\"property\":\"memberOf\",\"value\":\"grp-1\",\"displayValue\":\"Group One\","
            + "\"classifications\":null}],\"numEntitlements\":1}";

    private static RoleEntitlementService svc() {
        return new RoleEntitlementService(null); // client unused by pure parsers
    }

    @Test
    void parsesGrantsWithVerifiedFields() {
        List<RoleEntitlementGrant> grants = svc().parseGrants(JSON, "role-1");
        assertEquals(1, grants.size());
        RoleEntitlementGrant g = grants.get(0);
        assertEquals("role-1", g.roleId());
        assertEquals("EntraAuth", g.applicationName());
        assertEquals("memberOf", g.property());
        assertEquals("grp-1", g.value());
        assertEquals("Group One", g.displayValue());
        assertEquals(1, svc().parseTotal(JSON));
    }

    @Test
    void emptyGridYieldsNoGrants() {
        String empty = "{\"entitlements\":[],\"numEntitlements\":0}";
        assertTrue(svc().parseGrants(empty, "role-2").isEmpty());
        assertEquals(0, svc().parseTotal(empty));
    }
}
