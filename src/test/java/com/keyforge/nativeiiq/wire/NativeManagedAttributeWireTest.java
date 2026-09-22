package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeAssociationRef;
import com.keyforge.nativeiiq.model.NativeManagedAttributeExtractionResult;
import com.keyforge.nativeiiq.model.NativeManagedAttributeRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;
import com.keyforge.nativeiiq.model.NativeReferenceRef;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the ManagedAttribute wire envelope keeps supported fields and serializes cleanly. */
class NativeManagedAttributeWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "value", "displayName", "displayableName", "attribute", "type", "uuid",
            "referenceAttribute", "purview", "applicationId", "applicationName", "instance", "nativeIdentity",
            "requestable", "group", "permission", "uncorrelated", "aggregated", "iiqElevatedAccess",
            "ownerId", "ownerName", "description", "descriptions", "permissions", "targetPermissions",
            "inheritance", "associations", "attributes", "created", "modified", "lastRefresh",
            "lastTargetAggregation", "srcSystem", "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeManagedAttributeRow sampleRow() {
        NativeManagedAttributeRow r = new NativeManagedAttributeRow();
        r.setSourceId("0a1b2c");
        r.setName("CN=Admins,OU=Groups");
        r.setValue("CN=Admins,OU=Groups");
        r.setDisplayName("Admins");
        r.setDisplayableName("Admins");
        r.setAttribute("memberOf");
        r.setType("group");
        r.setApplicationId("app-1");
        r.setApplicationName("Active Directory");
        r.setRequestable(Boolean.TRUE);
        r.setGroup(Boolean.TRUE);
        r.setPermission(Boolean.FALSE);
        r.setUncorrelated(Boolean.FALSE);
        r.setOwnerId("own-1");
        r.setOwnerName("spadmin");
        r.setDescription("Administrators group");
        r.getDescriptions().put("en_US", "Administrators group");
        r.getPermissions().add(new NativePermissionRef("folderA", "read,write", null));
        r.getTargetPermissions().add(new NativePermissionRef("share1", "read", "note"));
        r.getInheritance().add(new NativeReferenceRef("parent-1", "CN=AllStaff"));
        r.getAssociations().add(new NativeAssociationRef("grpX", "group", "ManagedAttribute", "AD", "obj-1"));
        r.getAttributes().put("iiqDisabled", JsonSafe.toJsonSafe(Boolean.FALSE));
        r.setCreated(Instant.EPOCH);
        r.setModified(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeKeepsAllSupportedFields() {
        NativeManagedAttributeExtractionResult result =
                new NativeManagedAttributeExtractionResult("IdentityIQ", "run-1", "ManagedAttribute", Instant.EPOCH);
        result.getManagedAttributes().add(sampleRow());

        Map<String, Object> env = NativeManagedAttributeWire.envelope(result, 0, 3);
        assertEquals("ManagedAttribute", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("CN=Admins,OU=Groups", row.get("value"));
        assertEquals("native_iiq_java_api", row.get("srcInterface"));
        assertEquals("sailpoint.object.ManagedAttribute", row.get("srcObjectType"));
    }

    @Test
    void envelopeSerializesCleanlyWithJackson() {
        NativeManagedAttributeExtractionResult result =
                new NativeManagedAttributeExtractionResult("IdentityIQ", "run-1", "ManagedAttribute", Instant.EPOCH);
        result.getManagedAttributes().add(sampleRow());
        Map<String, Object> env = NativeManagedAttributeWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(env));
        assertTrue(json.contains("Active Directory"));
        assertTrue(json.contains("\"returned\":1"));
    }

    /**
     * Response contract: the resource hands the JSON provider the envelope Map, serialized once to a
     * JSON OBJECT. Guards the double-encoding regression (a pre-serialized String re-encoded into a
     * quoted JSON string), using the same Jackson engine IIQ's provider uses.
     */
    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeManagedAttributeExtractionResult result =
                new NativeManagedAttributeExtractionResult("IdentityIQ", "run-1", "ManagedAttribute", Instant.EPOCH);
        result.getManagedAttributes().add(sampleRow());
        Map<String, Object> env = NativeManagedAttributeWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"ManagedAttribute\""), "keys must be unquoted object keys");

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeManagedAttributeWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("ManagedAttribute", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
