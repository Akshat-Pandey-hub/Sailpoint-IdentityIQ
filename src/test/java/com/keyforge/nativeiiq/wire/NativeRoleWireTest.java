package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeRoleExtractionResult;
import com.keyforge.nativeiiq.model.NativeRoleRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the Role wire envelope keeps supported fields and is a JSON object (not double-encoded). */
class NativeRoleWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "displayName", "displayableName", "fullName", "description", "type",
            "assignmentId", "activityEnabled", "allowMultipleAssignments", "iiqElevatedAccess",
            "pendingDelete", "hasSelector", "riskScoreWeight", "ownerId", "ownerName", "activationDate",
            "deactivationDate", "descriptions", "attributes", "created", "modified", "srcSystem",
            "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeRoleRow sampleRow() {
        NativeRoleRow r = new NativeRoleRow();
        r.setSourceId("0a1b2c");
        r.setName("Engineer");
        r.setDisplayName("Engineer");
        r.setDisplayableName("Engineer");
        r.setFullName("Engineer");
        r.setDescription("Engineering role");
        r.setType("business");
        r.setActivityEnabled(Boolean.TRUE);
        r.setAllowMultipleAssignments(Boolean.FALSE);
        r.setIiqElevatedAccess(Boolean.FALSE);
        r.setPendingDelete(Boolean.FALSE);
        r.setHasSelector(Boolean.TRUE);
        r.setRiskScoreWeight(Integer.valueOf(300));
        r.setOwnerId("own-1");
        r.setOwnerName("spadmin");
        r.getDescriptions().put("en_US", "Engineering role");
        r.getAttributes().put("customAttr", JsonSafe.toJsonSafe("val"));
        r.setCreated(Instant.EPOCH);
        r.setModified(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeKeepsSupportedFields() {
        NativeRoleExtractionResult result =
                new NativeRoleExtractionResult("IdentityIQ", "run-1", "Bundle", Instant.EPOCH);
        result.getRoles().add(sampleRow());

        Map<String, Object> env = NativeRoleWire.envelope(result, 0, 3);
        assertEquals("Bundle", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("Engineer", row.get("name"));
        assertEquals("sailpoint.object.Bundle", row.get("srcObjectType"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeRoleExtractionResult result =
                new NativeRoleExtractionResult("IdentityIQ", "run-1", "Bundle", Instant.EPOCH);
        result.getRoles().add(sampleRow());
        Map<String, Object> env = NativeRoleWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"Bundle\""));

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeRoleWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("Bundle", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
