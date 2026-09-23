package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeAccountEntitlementExtractionResult;
import com.keyforge.nativeiiq.model.NativeAccountEntitlementRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the account-entitlement wire envelope carries link counts and is a JSON object. */
class NativeAccountEntitlementWireTest {

    private static NativeAccountEntitlementRow row(String value) {
        NativeAccountEntitlementRow r = new NativeAccountEntitlementRow();
        r.setLinkId("l1");
        r.setIdentityName("alice");
        r.setApplicationName("AD");
        r.setNativeIdentity("cn=alice");
        r.setAttributeName("memberOf");
        r.setAttributeValue(value);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        r.setExtractedAt(Instant.EPOCH);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopeCarriesEdgesAndLinkCounts() {
        NativeAccountEntitlementExtractionResult result =
                new NativeAccountEntitlementExtractionResult("IdentityIQ", "run-1", "AccountEntitlement", Instant.EPOCH);
        result.setSourceCount(5);
        result.setLinkCount(2);
        result.getRows().add(row("g1"));
        result.getRows().add(row("g2"));

        Map<String, Object> env = NativeAccountEntitlementWire.envelope(result, 0, 200);
        assertEquals("AccountEntitlement", env.get("entity"));
        assertEquals(Integer.valueOf(2), env.get("returned"));
        assertEquals(Integer.valueOf(2), env.get("returnedLinks"));
        assertEquals(Integer.valueOf(5), env.get("sourceCount"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        assertEquals("g1", rows.get(0).get("attributeValue"));
        assertEquals("sailpoint.object.Link.entitlementAttributes", rows.get(0).get("srcObjectType"));
    }

    @Test
    void envelopeIsAJsonObject() throws Exception {
        NativeAccountEntitlementExtractionResult result =
                new NativeAccountEntitlementExtractionResult("IdentityIQ", "run-1", "AccountEntitlement", Instant.EPOCH);
        result.getRows().add(row("g1"));
        String once = new ObjectMapper().writeValueAsString(NativeAccountEntitlementWire.envelope(result, 0, 200));
        assertTrue(once.startsWith("{"));
    }
}
