package com.keyforge.iiq.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Policy grid parsing (envelope objects/count) + mapping, using the authoritative fields. */
class PolicyTest {

    private static PolicyService svc() {
        return new PolicyService(null); // client unused by the pure parser
    }

    @Test
    void emptyGridYieldsNoPolicies() {
        String empty = "{\"objects\":[],\"count\":0,\"success\":true}";
        assertTrue(svc().parsePolicies(empty).isEmpty());
        assertEquals(0, svc().parseTotal(empty));
    }

    @Test
    void parsesPolicyRows() {
        String json = "{\"objects\":[{\"id\":\"7f0001019fbf1a17819fc7e670310ee7\",\"name\":\"SoD-Finance\","
                + "\"type\":\"SOD\",\"state\":\"Active\",\"description\":\"AP/AR separation\"}],\"count\":1}";
        List<PolicyDefinition> ps = svc().parsePolicies(json);
        assertEquals(1, ps.size());
        assertEquals("SoD-Finance", ps.get(0).name());
        assertEquals("SOD", ps.get(0).type());
        assertEquals("Active", ps.get(0).state());
        assertEquals(1, svc().parseTotal(json));
    }

    @Test
    void mapsCanonicalPolicyId() {
        PolicyRow row = PolicyRowMapper.map(new PolicyDefinition(
                "7f0001019fbf1a17819fc7e670310ee7", "SoD-Finance", "SOD", "Active", "desc"));
        assertEquals("7f000101-9fbf-1a17-819f-c7e670310ee7", row.policyid());
        assertEquals("SoD-Finance", row.name());
        assertEquals("SOD", row.type());
    }
}
