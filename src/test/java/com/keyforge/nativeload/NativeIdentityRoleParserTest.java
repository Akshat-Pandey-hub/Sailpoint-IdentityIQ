package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing (assigned + detected edges) + fail-loud for the native identity-role payload. */
class NativeIdentityRoleParserTest {

    private final NativeIdentityRoleParser parser = new NativeIdentityRoleParser();

    @Test
    void parsesAssignedAndDetectedEdgesWithCountsAndTargets() {
        String json = "{\"sourceCount\":10,\"returnedIdentities\":2,\"rows\":["
                + "{\"identityId\":\"id1\",\"roleId\":\"r1\",\"roleName\":\"Admin\",\"relationshipType\":\"ASSIGNED\","
                + "\"assignmentId\":\"asg1\",\"futureAssignment\":false,"
                + "\"targets\":[{\"applicationName\":\"AD\",\"nativeIdentity\":\"cn=alice\"}]},"
                + "{\"identityId\":\"id1\",\"roleId\":\"r2\",\"roleName\":\"IT-AD\",\"relationshipType\":\"DETECTED\","
                + "\"detectionAssignmentIds\":\"asg1\"}]}";
        List<NativeIdentityRoleRecord> rows = parser.parse(json);
        assertEquals(2, rows.size());
        assertEquals(10, parser.sourceCount(json));
        assertEquals(2, parser.returnedIdentities(json));
        assertEquals("ASSIGNED", rows.get(0).relationshipType);
        assertTrue(rows.get(0).targetsJson.contains("AD"));
        assertEquals("DETECTED", rows.get(1).relationshipType);
        assertEquals("asg1", rows.get(1).detectionAssignmentIds);
    }

    @Test
    void parsesAssignmentProvenanceFieldsWhenPresentAndNullWhenAbsent() {
        String json = "{\"rows\":["
                + "{\"identityId\":\"id1\",\"roleId\":\"r1\",\"relationshipType\":\"ASSIGNED\",\"assignmentId\":\"asg1\","
                + "\"assigner\":\"spadmin\",\"assignedDate\":\"2026-10-02T09:00:00Z\","
                + "\"startDate\":\"2026-10-02T09:00:00Z\",\"endDate\":\"2026-12-31T00:00:00Z\","
                + "\"source\":\"LCM\",\"negative\":false,\"manual\":true},"
                + "{\"identityId\":\"id1\",\"roleId\":\"r2\",\"relationshipType\":\"DETECTED\","
                + "\"detectionAssignmentIds\":\"asg1\"}]}";
        List<NativeIdentityRoleRecord> rows = parser.parse(json);

        NativeIdentityRoleRecord assigned = rows.get(0);
        assertEquals("spadmin", assigned.assigner);
        assertEquals(Instant.parse("2026-10-02T09:00:00Z"), assigned.assignedDate);
        assertEquals(Instant.parse("2026-10-02T09:00:00Z"), assigned.startDate);
        assertEquals(Instant.parse("2026-12-31T00:00:00Z"), assigned.endDate);
        assertEquals("LCM", assigned.source);
        assertEquals(Boolean.FALSE, assigned.negative);
        assertEquals(Boolean.TRUE, assigned.manual);

        NativeIdentityRoleRecord detected = rows.get(1);
        assertNull(detected.assigner);
        assertNull(detected.assignedDate);
        assertNull(detected.startDate);
        assertNull(detected.endDate);
        assertNull(detected.source);
        assertNull(detected.negative);
        assertNull(detected.manual);
    }

    @Test
    void emptyRolesButIdentitiesPresentReturnsEmptyList() {
        assertTrue(parser.parse("{\"sourceCount\":1,\"returnedIdentities\":1,\"rows\":[]}").isEmpty());
        assertEquals(1, parser.returnedIdentities("{\"sourceCount\":1,\"returnedIdentities\":1,\"rows\":[]}"));
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"IdentityRole\"}"));
    }
}
