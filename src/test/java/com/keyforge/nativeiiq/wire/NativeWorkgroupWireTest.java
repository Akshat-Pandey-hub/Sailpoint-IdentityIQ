package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeWorkgroupExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkgroupRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the Workgroup wire envelope keeps supported fields and is a JSON object (not double-encoded). */
class NativeWorkgroupWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "displayName", "displayableName", "email", "type", "description",
            "notificationOption", "inactive", "workgroup", "ownerId", "ownerName", "capabilities",
            "attributes", "created", "modified", "srcSystem", "srcInterface", "srcObjectType",
            "extractionRunId", "extractedAt"
    };

    private static NativeWorkgroupRow sampleRow() {
        NativeWorkgroupRow r = new NativeWorkgroupRow();
        r.setSourceId("0a1b2c");
        r.setName("Security Admins");
        r.setDisplayName("Security Admins");
        r.setDisplayableName("Security Admins");
        r.setEmail("sec@example.com");
        r.setType("workgroup");
        r.setDescription("Security governance workgroup");
        r.setNotificationOption("Both");
        r.setInactive(Boolean.FALSE);
        r.setWorkgroup(Boolean.TRUE);
        r.setOwnerId("own-1");
        r.setOwnerName("spadmin");
        r.getCapabilities().add("SystemAdministrator");
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
        NativeWorkgroupExtractionResult result =
                new NativeWorkgroupExtractionResult("IdentityIQ", "run-1", "Workgroup", Instant.EPOCH);
        result.getWorkgroups().add(sampleRow());

        Map<String, Object> env = NativeWorkgroupWire.envelope(result, 0, 3);
        assertEquals("Workgroup", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("Security Admins", row.get("name"));
        assertEquals(Boolean.TRUE, row.get("workgroup"));
        assertEquals("sailpoint.object.Identity", row.get("srcObjectType"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeWorkgroupExtractionResult result =
                new NativeWorkgroupExtractionResult("IdentityIQ", "run-1", "Workgroup", Instant.EPOCH);
        result.getWorkgroups().add(sampleRow());
        Map<String, Object> env = NativeWorkgroupWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"Workgroup\""));

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeWorkgroupWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("Workgroup", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
