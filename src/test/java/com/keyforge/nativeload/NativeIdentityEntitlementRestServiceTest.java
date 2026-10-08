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
 * Contract for the KF Agent Identity-Entitlement read service: reuses the native paging loop + parser
 * over a fake page source (no live IIQ, no DB), returns our DB-named fields, excludes the entitlementid
 * PK + lineage, serializes {@code value_list} as JSON while the other structured fields stay strings,
 * preserves null/empty, and supports generic exact filtering on any scalar/text field.
 */
class NativeIdentityEntitlementRestServiceTest {

    private final NativeIdentityEntitlementRestService svc = new NativeIdentityEntitlementRestService();

    private static String groupMember() {
        return "{\"sourceId\":\"ie1\",\"identityId\":\"i1\",\"identityName\":\"Alexander Evans\","
                + "\"applicationId\":\"a1\",\"applicationName\":\"EntraAuth\",\"nativeIdentity\":\"179fcc53\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\","
                + "\"valueList\":[\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\",\"02cf391b-36ae-4fb2-bcca-3c12448d9560\"],"
                + "\"type\":\"Entitlement\",\"assigned\":false,\"grantedByRole\":false,\"allowed\":true,"
                + "\"connected\":true,\"aggregationState\":\"Connected\",\"source\":\"Aggregation\","
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.IdentityEntitlement\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    private static String assignedRole() {
        return "{\"sourceId\":\"ie2\",\"identityId\":\"i2\",\"identityName\":\"App_IDJ0001008\","
                + "\"applicationName\":\"Workday\",\"attributeName\":\"role\",\"attributeValue\":\"Viewer\","
                + "\"assigned\":true,\"grantedByRole\":false,\"source\":\"LCM\",\"assignmentId\":\"asg-9\"}";
    }

    private static NativeIdentityEntitlementPageSource source() {
        String envelope = "{\"entity\":\"IdentityEntitlement\",\"rows\":[" + groupMember() + "," + assignedRole() + "]}";
        String empty = "{\"entity\":\"IdentityEntitlement\",\"rows\":[]}";
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
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("ie1", r.get("source_id"));
        assertEquals("Alexander Evans", r.get("identity_name"));
        assertEquals("EntraAuth", r.get("application_name"));
        assertEquals("groups", r.get("attribute_name"));
        assertEquals("00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5", r.get("attribute_value"));
        assertEquals(Boolean.FALSE, r.get("assigned"));
        assertEquals(Boolean.TRUE, r.get("allowed"));
        assertEquals(33, r.size(), "exactly the SailPoint-facing Identity-Entitlement fields");

        assertFalse(r.containsKey("entitlementid"));
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
    void valueListIsJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> g = rows.get(0);
        assertTrue(g.get("value_list") instanceof JsonNode, "value_list is jsonb -> JSON");
        assertTrue(((JsonNode) g.get("value_list")).isArray());
        assertEquals(2, ((JsonNode) g.get("value_list")).size());
        // unset structured/text/date fields stay null (not manufactured)
        assertNull(g.get("source_assignable_roles"));
        assertNull(g.get("assignment_id"));
        assertNull(g.get("start_date"));

        // row 2 has no valueList -> null
        assertNull(rows.get(1).get("value_list"), "missing value_list stays null");
        assertEquals("asg-9", rows.get(1).get("assignment_id"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Evans"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("application_name", "EntraAuth"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("attribute_name", "groups"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("attribute_value", "00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("assigned", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("assigned", "true"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Evans", "assigned", "false"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("identity_name", "Alexander Evans", "assigned", "true"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // value_list jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("value_list", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("ie2", win.get(0).get("source_id"));
    }
}
