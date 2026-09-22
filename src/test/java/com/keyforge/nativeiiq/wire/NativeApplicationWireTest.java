package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeApplicationExtractionResult;
import com.keyforge.nativeiiq.model.NativeApplicationRow;
import com.keyforge.nativeiiq.model.NativeReferenceRef;
import com.keyforge.nativeiiq.model.NativeSchemaRef;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the Application wire envelope keeps supported fields and is a JSON object (not double-encoded). */
class NativeApplicationWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "description", "type", "connector", "featuresString", "authoritative",
            "logical", "composite", "supportsProvisioning", "ownerId", "ownerName", "secondaryOwners",
            "remediators", "dependencies", "schemas", "descriptions", "attributes", "created", "modified",
            "srcSystem", "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeApplicationRow sampleRow() {
        NativeApplicationRow r = new NativeApplicationRow();
        r.setSourceId("0a1b2c");
        r.setName("Active Directory");
        r.setDescription("AD connector");
        r.setType("ActiveDirectory - Direct");
        r.setConnector("sailpoint.connector.ADLDAPConnector");
        r.setFeaturesString("PROVISIONING, AUTHENTICATE");
        r.setAuthoritative(Boolean.FALSE);
        r.setLogical(Boolean.FALSE);
        r.setComposite(Boolean.FALSE);
        r.setSupportsProvisioning(Boolean.TRUE);
        r.setOwnerId("own-1");
        r.setOwnerName("spadmin");
        r.getSecondaryOwners().add(new NativeReferenceRef("id2", "Jane"));
        r.getRemediators().add(new NativeReferenceRef("id3", "Bob"));
        r.getDependencies().add(new NativeReferenceRef("app2", "LDAP"));
        r.getSchemas().add(new NativeSchemaRef("account", "user", "sAMAccountName", "displayName", null, Integer.valueOf(12)));
        r.getDescriptions().put("en_US", "AD connector");
        r.getAttributes().put("host", JsonSafe.toJsonSafe("dc01.example.com"));
        r.getAttributes().put("password", "<redacted>");
        r.setCreated(Instant.EPOCH);
        r.setModified(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeKeepsSupportedFields() {
        NativeApplicationExtractionResult result =
                new NativeApplicationExtractionResult("IdentityIQ", "run-1", "Application", Instant.EPOCH);
        result.getApplications().add(sampleRow());

        Map<String, Object> env = NativeApplicationWire.envelope(result, 0, 3);
        assertEquals("Application", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("Active Directory", row.get("name"));
        assertEquals("sailpoint.object.Application", row.get("srcObjectType"));
        assertEquals("<redacted>", ((Map<String, Object>) row.get("attributes")).get("password"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeApplicationExtractionResult result =
                new NativeApplicationExtractionResult("IdentityIQ", "run-1", "Application", Instant.EPOCH);
        result.getApplications().add(sampleRow());
        Map<String, Object> env = NativeApplicationWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"Application\""));

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeApplicationWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("Application", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
