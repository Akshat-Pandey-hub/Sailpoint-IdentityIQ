package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeAccountRef;
import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRow;
import com.keyforge.nativeiiq.model.NativeRoleAssignmentRef;
import com.keyforge.nativeiiq.model.NativeRoleDetectionRef;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the wire envelope keeps all supported native fields and is JSON-serializable end-to-end
 * (using the same Jackson engine IIQ's {@code JsonHelper} uses), including an extended attribute that
 * has been run through {@link JsonSafe}.
 */
class NativeIdentityWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "displayName", "displayableName", "firstName", "lastName", "email",
            "inactive", "type", "correlated", "managerId", "managerName", "administratorId",
            "administratorName", "accounts", "assignedRoles", "detectedRoles", "roleAssignments",
            "roleDetections", "capabilities", "controlledScopes", "created", "modified", "lastRefresh",
            "lastLogin", "srcSystem", "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeIdentityRow sampleRow() {
        NativeIdentityRow r = new NativeIdentityRow();
        r.setSourceId("0a1b2c");
        r.setName("jsmith");
        r.setDisplayName("John Smith");
        r.setDisplayableName("John Smith");
        r.setFirstName("John");
        r.setLastName("Smith");
        r.setEmail("john@example.com");
        r.setInactive(Boolean.FALSE);
        r.setType("employee");
        r.setCorrelated(Boolean.TRUE);
        r.setManagerStatus(Boolean.FALSE);
        r.setManagerId("mgr-1");
        r.setManagerName("Boss");
        r.setAdministratorId("adm-1");
        r.setAdministratorName("Admin");
        r.getAccounts().add(new NativeAccountRef("AD", "cn=jsmith", "east", "John Smith"));
        r.getAssignedRoles().add("Engineer");
        r.getDetectedRoles().add("Contractor");
        r.getRoleAssignments().add(new NativeRoleAssignmentRef("Engineer", "role-1", "granted"));
        r.getRoleDetections().add(new NativeRoleDetectionRef("Contractor", "role-2", Instant.EPOCH, "a1"));
        r.getCapabilities().add("SystemAdministrator");
        r.getControlledScopes().add("US");
        // attribute value already JSON-safe (as the mapper produces via JsonSafe)
        r.getAttributes().put("department", JsonSafe.toJsonSafe("IT"));
        r.setCreated(Instant.EPOCH);
        r.setModified(Instant.EPOCH);
        r.setLastRefresh(Instant.EPOCH);
        r.setLastLogin(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeKeepsAllSupportedFieldsAndReportsCount() {
        NativeExtractionResult result =
                new NativeExtractionResult("IdentityIQ", "run-1", "Identity", Instant.EPOCH);
        result.getIdentities().add(sampleRow());
        result.setFinishedAt(Instant.EPOCH);

        Map<String, Object> env = NativeIdentityWire.envelope(result, 0, 3);
        assertEquals("Identity", env.get("entity"));
        assertEquals(Integer.valueOf(0), env.get("start"));
        assertEquals(Integer.valueOf(3), env.get("limit"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("jsmith", row.get("name"));
        assertEquals("native_iiq_java_api", row.get("srcInterface"));
        assertEquals("IT", ((Map<String, Object>) row.get("attributes")).get("department"));
    }

    @Test
    void envelopeSerializesCleanlyWithJackson() {
        NativeExtractionResult result =
                new NativeExtractionResult("IdentityIQ", "run-1", "Identity", Instant.EPOCH);
        result.getIdentities().add(sampleRow());

        Map<String, Object> env = NativeIdentityWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(env));
        assertTrue(json.contains("\"name\":\"jsmith\""));
        assertTrue(json.contains("\"returned\":1"));
    }

    /**
     * Response contract: the resource hands the JSON provider the envelope <b>Map</b>, which serializes
     * once to a JSON OBJECT. Guards against the double-encoding regression where a pre-serialized
     * {@code JsonHelper.toJson(...)} String was handed to the provider and re-encoded into a quoted
     * JSON string value ({@code "{\"entity\":...}"}). Uses the same Jackson engine IIQ's provider uses.
     */
    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeExtractionResult result =
                new NativeExtractionResult("IdentityIQ", "run-1", "Identity", Instant.EPOCH);
        result.getIdentities().add(sampleRow());
        Map<String, Object> env = NativeIdentityWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        // Correct: provider serializes the Map once -> a JSON OBJECT.
        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"Identity\""), "keys must be unquoted object keys");

        // The bug that was fixed: handing the provider a pre-serialized String double-encodes it.
        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"),
                "handing the provider a JSON String re-encodes it into a quoted string value (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesAnyThrowableIncludingErrorAndSerializes() {
        // The resource catches Throwable (broader than Exception) because IIQ's plugin-REST mappers
        // only handle Exception, so an Error would otherwise escape to exception.jsf. The error body
        // must name that Error's type/message and serialize cleanly.
        Throwable err = new NoClassDefFoundError("sailpoint/example/Missing");
        Map<String, Object> env = NativeIdentityWire.errorEnvelope(500, err);
        assertEquals("Identity", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        Map<String, Object> error = (Map<String, Object>) env.get("error");
        assertEquals("java.lang.NoClassDefFoundError", error.get("type"));
        assertEquals("sailpoint/example/Missing", error.get("message"));

        ObjectMapper mapper = new ObjectMapper();
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(env));
        assertTrue(json.contains("java.lang.NoClassDefFoundError"));
    }
}
