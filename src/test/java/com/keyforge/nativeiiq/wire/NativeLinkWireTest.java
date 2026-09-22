package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeLinkExtractionResult;
import com.keyforge.nativeiiq.model.NativeLinkRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the Link/account wire envelope keeps supported fields and is a JSON object (not double-encoded). */
class NativeLinkWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "uuid", "nativeIdentity", "displayName", "displayableName", "instance", "componentIds",
            "applicationId", "applicationName", "identityId", "identityName", "disabled", "locked", "composite",
            "manuallyCorrelated", "hasEntitlements", "iiqDisabled", "iiqLocked", "permissions",
            "targetPermissions", "attributes", "entitlementAttributes", "created", "modified", "lastRefresh",
            "lastTargetAggregation", "srcSystem", "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeLinkRow sampleRow() {
        NativeLinkRow r = new NativeLinkRow();
        r.setSourceId("0a1b2c");
        r.setNativeIdentity("CN=jsmith,OU=Users");
        r.setDisplayName("jsmith");
        r.setDisplayableName("John Smith");
        r.setApplicationId("app-1");
        r.setApplicationName("Active Directory");
        r.setIdentityId("id-1");
        r.setIdentityName("jsmith");
        r.setDisabled(Boolean.FALSE);
        r.setLocked(Boolean.FALSE);
        r.setComposite(Boolean.FALSE);
        r.setManuallyCorrelated(Boolean.FALSE);
        r.setHasEntitlements(Boolean.TRUE);
        r.setIiqDisabled(Boolean.FALSE);
        r.getPermissions().add(new NativePermissionRef("folderA", "read", null));
        r.getAttributes().put("memberOf", JsonSafe.toJsonSafe("CN=Admins"));
        r.getAttributes().put("password", "<redacted>");
        r.getEntitlementAttributes().put("memberOf", JsonSafe.toJsonSafe("CN=Admins"));
        r.setCreated(Instant.EPOCH);
        r.setModified(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeKeepsSupportedFields() {
        NativeLinkExtractionResult result =
                new NativeLinkExtractionResult("IdentityIQ", "run-1", "Link", Instant.EPOCH);
        result.getLinks().add(sampleRow());

        Map<String, Object> env = NativeLinkWire.envelope(result, 0, 3);
        assertEquals("Link", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (String key : REQUIRED_KEYS) {
            assertTrue(row.containsKey(key), "row must contain field: " + key);
        }
        assertEquals("CN=jsmith,OU=Users", row.get("nativeIdentity"));
        assertEquals("sailpoint.object.Link", row.get("srcObjectType"));
        assertEquals("<redacted>", ((Map<String, Object>) row.get("attributes")).get("password"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeLinkExtractionResult result =
                new NativeLinkExtractionResult("IdentityIQ", "run-1", "Link", Instant.EPOCH);
        result.getLinks().add(sampleRow());
        Map<String, Object> env = NativeLinkWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"Link\""));

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeLinkWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("Link", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
