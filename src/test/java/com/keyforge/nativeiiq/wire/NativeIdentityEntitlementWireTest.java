package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeIdentityEntitlementExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityEntitlementRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the IdentityEntitlement wire envelope preserves provenance and is a JSON object. */
class NativeIdentityEntitlementWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "identityId", "identityName", "applicationName", "nativeIdentity", "attributeName",
            "attributeValue", "type", "assigned", "grantedByRole", "aggregationState", "source", "sourceObject",
            "assigner", "certificationItemId", "requestItemId", "srcObjectType", "extractionRunId"
    };

    private static NativeIdentityEntitlementRow row() {
        NativeIdentityEntitlementRow r = new NativeIdentityEntitlementRow();
        r.setSourceId("ie1");
        r.setIdentityName("alice");
        r.setApplicationName("AD");
        r.setNativeIdentity("cn=alice");
        r.setAttributeName("memberOf");
        r.setAttributeValue("cn=admins");
        r.setGrantedByRole(Boolean.TRUE);
        r.setSource("Rule");
        r.setSourceObject("Rule");
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        r.setExtractedAt(Instant.EPOCH);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeCarriesRowsProvenanceAndSourceCount() {
        NativeIdentityEntitlementExtractionResult result =
                new NativeIdentityEntitlementExtractionResult("IdentityIQ", "run-1", "IdentityEntitlement", Instant.EPOCH);
        result.setSourceCount(1);
        result.getRows().add(row());

        Map<String, Object> env = NativeIdentityEntitlementWire.envelope(result, 0, 500);
        assertEquals("IdentityEntitlement", env.get("entity"));
        assertEquals(Integer.valueOf(1), env.get("returned"));
        assertEquals(Integer.valueOf(1), env.get("sourceCount"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        for (String key : REQUIRED_KEYS) {
            assertTrue(rows.get(0).containsKey(key), "row must contain field: " + key);
        }
        assertEquals(Boolean.TRUE, rows.get(0).get("grantedByRole"));
        assertEquals("sailpoint.object.IdentityEntitlement", rows.get(0).get("srcObjectType"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeIdentityEntitlementExtractionResult result =
                new NativeIdentityEntitlementExtractionResult("IdentityIQ", "run-1", "IdentityEntitlement", Instant.EPOCH);
        result.getRows().add(row());
        Map<String, Object> env = NativeIdentityEntitlementWire.envelope(result, 0, 500);
        ObjectMapper mapper = new ObjectMapper();
        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeIdentityEntitlementWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("IdentityEntitlement", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
