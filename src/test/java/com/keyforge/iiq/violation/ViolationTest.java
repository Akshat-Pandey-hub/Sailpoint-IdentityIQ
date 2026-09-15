package com.keyforge.iiq.violation;

import com.keyforge.iiq.client.IiqApiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SCIM /PolicyViolations parsing + mapping; verified fields, NULL for unexposed provenance. */
class ViolationTest {

    private static IiqApiClient fake(String body) {
        return new IiqApiClient() {
            @Override public String get(String path, Map<String, String> q) {
                assertEquals(ViolationService.VIOLATIONS_PATH, path);
                return body;
            }
        };
    }

    @Test
    void emptySourceYieldsNoViolations() {
        assertTrue(new ViolationService(fake("{\"totalResults\":0,\"Resources\":[]}"))
                .getAllViolations().isEmpty());
    }

    @Test
    void parsesViolationWithRefs() {
        String v = "{\"id\":\"7f0001019fbf1a17819fc7e670310ee7\",\"policyName\":\"SoD-Finance\","
                + "\"constraintName\":\"AP-AR\",\"status\":\"Open\",\"description\":\"conflict\","
                + "\"owner\":{\"value\":\"7f000101971416688197147684ad00ff\",\"displayName\":\"Molly J\"},"
                + "\"identity\":{\"value\":\"7f00010198421229819849ebc9ef0c71\",\"displayName\":\"Alexander Evans\"}}";
        List<PolicyViolation> vs = new ViolationService(
                fake("{\"totalResults\":1,\"Resources\":[" + v + "]}")).getAllViolations();
        assertEquals(1, vs.size());
        assertEquals("SoD-Finance", vs.get(0).policyName());
        assertEquals("Alexander Evans", vs.get(0).identity().displayName());
    }

    @Test
    void mapsCanonicalIdsAndNullProvenance() {
        PolicyViolation v = new PolicyViolation("7f0001019fbf1a17819fc7e670310ee7", "SoD-Finance", "AP-AR",
                "Open", "conflict",
                new PolicyViolation.Ref("7f000101971416688197147684ad00ff", null, "Molly J"),
                new PolicyViolation.Ref("7f00010198421229819849ebc9ef0c71", null, "Alexander Evans"));
        ViolationRow row = ViolationRowMapper.map(v);
        assertEquals("7f000101-9fbf-1a17-819f-c7e670310ee7", row.violationid());
        assertEquals("SoD-Finance", row.policyName());
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.ownerId());
        assertEquals("7f000101-9842-1229-8198-49ebc9ef0c71", row.identityId());
        assertEquals("Alexander Evans", row.identityDisplayName());
        assertNull(row.mitigator());
        assertNull(row.expirationDate());
    }

    @Test
    void nullRefsBecomeNullIds() {
        PolicyViolation v = new PolicyViolation("7f0001019fbf1a17819fc7e670310ee7", "P", "C", "Open", null,
                null, null);
        ViolationRow row = ViolationRowMapper.map(v);
        assertNull(row.ownerId());
        assertNull(row.identityId());
    }
}
