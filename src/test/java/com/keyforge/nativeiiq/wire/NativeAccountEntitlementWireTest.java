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
    @SuppressWarnings("unchecked")
    void envelopeSerializesPermissionEdgeFields() {
        NativeAccountEntitlementRow r = new NativeAccountEntitlementRow();
        r.setLinkId("l1");
        r.setApplicationName("UnixApp");
        r.setType("PERMISSION");
        r.setPermissionTarget("/finance");
        r.setPermissionRights("read,write");
        r.getPermissionRightsList().add("read");
        r.getPermissionRightsList().add("write");
        r.setPermissionAnnotation("Finance");
        r.setPermissionAggregationSource("agg");
        r.getPermissionAttributes().put("scope", "dept");
        r.setSrcObjectType("sailpoint.object.Link.permissions");

        NativeAccountEntitlementExtractionResult result =
                new NativeAccountEntitlementExtractionResult("IdentityIQ", "run-1", "AccountEntitlement", Instant.EPOCH);
        result.getRows().add(r);

        Map<String, Object> env = NativeAccountEntitlementWire.envelope(result, 0, 200);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        Map<String, Object> row = rows.get(0);
        assertEquals("PERMISSION", row.get("type"));
        assertEquals("/finance", row.get("permissionTarget"));
        assertEquals("read,write", row.get("permissionRights"));
        assertEquals("Finance", row.get("permissionAnnotation"));
        assertEquals("agg", row.get("permissionAggregationSource"));
        assertEquals(java.util.Arrays.asList("read", "write"), row.get("permissionRightsList"));
        assertEquals("dept", ((Map<String, Object>) row.get("permissionAttributes")).get("scope"));
        assertEquals("sailpoint.object.Link.permissions", row.get("srcObjectType"));
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
