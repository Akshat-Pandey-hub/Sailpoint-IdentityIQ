package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the KF Agent Account-Entitlement read service: reuses the native paging loop + parser over a
 * fake page source (no live IIQ, no DB), returns our 16 DB-named fields across the three edge {@code type}s
 * (ATTRIBUTE / PERMISSION / TARGET_PERMISSION), excludes the accountentitlementid PK + lineage, serializes
 * permission_rights_list / permission_attributes as real JSON while scalar fields stay strings, preserves
 * null, and supports generic exact filtering on scalar fields only.
 */
class NativeAccountEntitlementRestServiceTest {

    private final NativeAccountEntitlementRestService svc = new NativeAccountEntitlementRestService();

    /** ATTRIBUTE edge (Link.getEntitlementAttributes): permission_* unset. */
    private static String attributeEdge() {
        return "{\"linkId\":\"7f00..0dbf\",\"identityId\":\"7f00..0cc6\",\"identityName\":\"Alexander Roberts\","
                + "\"applicationId\":\"7f00..0d58\",\"applicationName\":\"EntraTarget\","
                + "\"nativeIdentity\":\"bc6eb61b-9b94-44e6-9e0d-2d58f96f1c2e\",\"type\":\"ATTRIBUTE\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"02cf391b-36ae-4fb2-bcca-3c12448d9560\","
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Link.entitlementAttributes\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    /** PERMISSION edge (Link.getPermissions): attribute_* unset, full permission payload incl. jsonb fields. */
    private static String permissionEdge() {
        return "{\"linkId\":\"7f00..0dbf\",\"identityId\":\"7f00..0cc6\",\"identityName\":\"Alexander Roberts\","
                + "\"applicationId\":\"7f00..0d58\",\"applicationName\":\"UnixApp\","
                + "\"nativeIdentity\":\"aroberts\",\"type\":\"PERMISSION\","
                + "\"permissionTarget\":\"/finance/reports\",\"permissionRights\":\"read,write\","
                + "\"permissionRightsList\":[\"read\",\"write\"],\"permissionAnnotation\":\"Finance reports\","
                + "\"permissionAggregationSource\":\"aggregation\","
                + "\"permissionAttributes\":{\"scope\":\"dept\"},"
                + "\"srcObjectType\":\"sailpoint.object.Link.permissions\"}";
    }

    /** TARGET_PERMISSION edge (Link.getTargetPermissions). */
    private static String targetPermissionEdge() {
        return "{\"linkId\":\"7f00..0d6f\",\"identityId\":\"7f00..0cc6\",\"identityName\":\"Alexander Evans\","
                + "\"applicationId\":\"7f00..0d58\",\"applicationName\":\"UnixApp\","
                + "\"nativeIdentity\":\"aevans\",\"type\":\"TARGET_PERMISSION\","
                + "\"permissionTarget\":\"/share/hr\",\"permissionRights\":\"read\","
                + "\"permissionRightsList\":[\"read\"],"
                + "\"srcObjectType\":\"sailpoint.object.Link.targetPermissions\"}";
    }

    private static NativeAccountEntitlementPageSource source() {
        String envelope = "{\"entity\":\"AccountEntitlement\",\"rows\":["
                + attributeEdge() + "," + permissionEdge() + "," + targetPermissionEdge() + "]}";
        String empty = "{\"entity\":\"AccountEntitlement\",\"rows\":[]}";
        return (start, limit) -> start == 0 ? envelope : empty;
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsSixteenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("7f00..0dbf", r.get("link_id"));
        assertEquals("Alexander Roberts", r.get("identity_name"));
        assertEquals("EntraTarget", r.get("application_name"));
        assertEquals("ATTRIBUTE", r.get("type"));
        assertEquals("groups", r.get("attribute_name"));
        assertEquals("02cf391b-36ae-4fb2-bcca-3c12448d9560", r.get("attribute_value"));
        assertNull(r.get("instance"), "null instance preserved");
        assertEquals(16, r.size(), "exactly the 16 SailPoint-facing fields");

        assertFalse(r.containsKey("accountentitlementid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void attributeEdgeHasNullPermissionFields() {
        Map<String, Object> a = svc.fetch(source(), null, null, null).get(0);
        assertNull(a.get("permission_target"));
        assertNull(a.get("permission_rights"));
        assertNull(a.get("permission_rights_list"));
        assertNull(a.get("permission_annotation"));
        assertNull(a.get("permission_aggregation_source"));
        assertNull(a.get("permission_attributes"));
    }

    @Test
    void permissionEdgePopulatesPermissionFieldsAndJsonStaysStructured() {
        Map<String, Object> p = svc.fetch(source(), null, null, null).get(1);
        assertEquals("PERMISSION", p.get("type"));
        assertEquals("/finance/reports", p.get("permission_target"));
        assertEquals("read,write", p.get("permission_rights"));
        assertEquals("Finance reports", p.get("permission_annotation"));
        assertEquals("aggregation", p.get("permission_aggregation_source"));
        // attribute_* unset on a permission edge
        assertNull(p.get("attribute_name"));
        assertNull(p.get("attribute_value"));

        // jsonb fields are real JSON, not stringified
        Object rightsList = p.get("permission_rights_list");
        assertTrue(rightsList instanceof JsonNode && ((JsonNode) rightsList).isArray());
        assertEquals("read", ((JsonNode) rightsList).get(0).asText());
        assertEquals("write", ((JsonNode) rightsList).get(1).asText());

        Object attrs = p.get("permission_attributes");
        assertTrue(attrs instanceof JsonNode && ((JsonNode) attrs).isObject());
        assertEquals("dept", ((JsonNode) attrs).get("scope").asText());
    }

    @Test
    void targetPermissionEdgeCarriesItsType() {
        Map<String, Object> t = svc.fetch(source(), null, null, null).get(2);
        assertEquals("TARGET_PERMISSION", t.get("type"));
        assertEquals("/share/hr", t.get("permission_target"));
        assertEquals("read", t.get("permission_rights"));
    }

    @Test
    void genericScalarFiltersIncludingTypeAndPermissionFields() {
        assertEquals(1, svc.fetch(source(), f("type", "ATTRIBUTE"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "PERMISSION"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "TARGET_PERMISSION"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("permission_target", "/finance/reports"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("permission_rights", "read,write"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("permission_annotation", "Finance reports"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("permission_aggregation_source", "aggregation"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("attribute_name", "groups"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("application_name", "UnixApp"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "PERMISSION", "application_name", "UnixApp"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "PERMISSION", "application_name", "EntraTarget"), null, null).size());
    }

    @Test
    void unknownAndJsonbFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("accountentitlementid", "x"), null, null));
        // the two jsonb permission fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("permission_rights_list", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("permission_attributes", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("PERMISSION", win.get(0).get("type"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("application_name", "UnixApp"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("application_name", "UnixApp"), 1, 5).size());
    }
}
