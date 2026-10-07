package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the KF Agent Entitlement read service: it reuses the native paging loop + parser over a
 * fake page source (no live IIQ, no DB), returns our 43 DB-named SailPoint fields, excludes KeyForge
 * PK/hash/lineage, and supports GENERIC exact filtering on any scalar response field (keys are our
 * response field names), applied over the full population before the output window.
 */
class NativeEntitlementRestServiceTest {

    private final NativeEntitlementRestService svc = new NativeEntitlementRestService();

    /** One native-wire row with the fields the tests exercise; other fields parse to null. */
    private static String row(String id, String name, String app, String type, boolean groupType, boolean group) {
        return "{\"sourceId\":\"" + id + "\",\"name\":" + js(name) + ",\"value\":\"v-" + id + "\","
                + "\"applicationName\":\"" + app + "\",\"type\":\"" + type + "\","
                + "\"groupType\":" + groupType + ",\"group\":" + group + ","
                + "\"attributes\":{\"k\":\"val-" + id + "\"},"
                // lineage fields the plugin emits at row level — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.ManagedAttribute\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-07T00:00:00Z\"}";
    }

    private static String js(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    /** Fake source: the whole population on page 0 (size < internal page size -> loop completes). */
    private static NativeManagedAttributePageSource source() {
        // Mirrors the live data: Entra groups have name=null, group=false, but type="group"/groupType=true.
        String envelope = "{\"entity\":\"ManagedAttribute\",\"rows\":["
                + row("e1", null, "EntraTarget", "group", true, false) + ","
                + row("e2", null, "EntraTarget", "group", true, false) + ","
                + row("e3", "Finance", "Workday", "Entitlement", false, false)
                + "]}";
        String empty = "{\"entity\":\"ManagedAttribute\",\"rows\":[]}";
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
    void returnsAllWithOurDbNamesAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("e1", r.get("source_id"));
        assertEquals("EntraTarget", r.get("application_name"));
        assertEquals("group", r.get("type"));
        assertEquals(Boolean.TRUE, r.get("is_group_type"));
        assertTrue(r.containsKey("source_hash"));
        assertEquals(43, r.size(), "exactly the 43 SailPoint-facing fields");

        assertFalse(r.containsKey("entitlementid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void nestedJsonbFieldIsAnObjectNotAString() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        assertTrue(r.get("attributes") instanceof com.fasterxml.jackson.databind.JsonNode);
    }

    @Test
    void genericFilterBySourceId() {
        List<Map<String, Object>> rows = svc.fetch(source(), f("source_id", "e2"), null, null);
        assertEquals(1, rows.size());
        assertEquals("e2", rows.get(0).get("source_id"));
    }

    @Test
    void genericFilterByTypeAndIsGroupTypeMatchesEntraGroups() {
        // These Entra groups are identified by type=group / is_group_type=true (NOT is_group).
        assertEquals(2, svc.fetch(source(), f("type", "group"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("is_group_type", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("is_group", "true"), null, null).size());
    }

    @Test
    void genericFilterByValueAndApplicationName() {
        assertEquals(1, svc.fetch(source(), f("value", "v-e1"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("application_name", "EntraTarget"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(2, svc.fetch(source(), f("application_name", "EntraTarget", "type", "group"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("application_name", "Workday", "type", "group"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(source(), f("bogus", "x"), null, null));
        // nested jsonb fields are not filterable either
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(source(), f("attributes", "{}"), null, null));
    }

    @Test
    void windowAppliesAfterFilteringAndDefaultsToAll() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("e2", win.get(0).get("source_id"));
    }
}
