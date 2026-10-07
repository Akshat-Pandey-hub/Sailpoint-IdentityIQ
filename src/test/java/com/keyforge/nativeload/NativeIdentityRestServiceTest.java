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
 * Contract for the KF Agent Identity read service: reuses the native paging loop + parser over a fake
 * page source (no live IIQ, no DB), returns our DB-named SailPoint Identity fields, excludes the userid
 * PK + lineage, serializes nested arrays/objects as JSON (not strings), preserves null, and supports
 * generic exact filtering on any scalar response field — applied over the full population before paging.
 */
class NativeIdentityRestServiceTest {

    private final NativeIdentityRestService svc = new NativeIdentityRestService();

    /** Row shaped like the real plugin wire (NativeIdentityWire keys). */
    private static String spadmin() {
        return "{\"sourceId\":\"7f0001\",\"name\":\"spadmin\",\"displayName\":\"Molly J\","
                + "\"displayableName\":\"Molly J\",\"firstName\":\"Molly\",\"lastName\":\"J\","
                + "\"email\":\"molly@corp.com\",\"inactive\":false,\"correlated\":false,"
                + "\"managerStatus\":false,\"isWorkgroup\":false,"
                + "\"accounts\":[],\"capabilities\":[\"SystemAdministrator\",\"Auditor\"],"
                + "\"controlledScopes\":[],\"attributes\":{\"firstname\":\"Molly\"},"
                + "\"score\":\"0\",\"fullName\":\"Molly J\",\"isProtected\":true,"
                // lineage fields the plugin emits — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Identity\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-07T00:00:00Z\"}";
    }

    private static String evans() {
        return "{\"sourceId\":\"7f0002\",\"name\":\"Alexander Evans\",\"displayName\":\"Alexander Evans\","
                + "\"email\":\"alex@corp.com\",\"inactive\":false,\"correlated\":true,"
                + "\"managerStatus\":false,\"isWorkgroup\":false,"
                + "\"accounts\":[{\"applicationName\":\"EntraTarget\",\"nativeIdentity\":\"179f\"}],"
                + "\"capabilities\":[],\"attributes\":{\"lastname\":\"Evans\"},\"score\":\"257\"}";
    }

    private static NativeIdentityPageSource source() {
        String envelope = "{\"entity\":\"Identity\",\"rows\":[" + spadmin() + "," + evans() + "]}";
        String empty = "{\"entity\":\"Identity\",\"rows\":[]}";
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
        assertEquals("7f0001", r.get("source_id"));
        assertEquals("spadmin", r.get("name"));
        assertEquals("molly@corp.com", r.get("email"));
        assertEquals(Boolean.FALSE, r.get("correlated"));
        assertEquals("0", r.get("score"));
        assertEquals(41, r.size(), "exactly the listed SailPoint-facing Identity fields");

        assertFalse(r.containsKey("userid"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void nestedFieldsAreJsonNotStringsAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        assertTrue(r.get("capabilities") instanceof JsonNode, "capabilities is a JSON array");
        assertTrue(((JsonNode) r.get("capabilities")).isArray());
        assertTrue(r.get("attributes") instanceof JsonNode, "attributes is a JSON object");
        assertTrue(r.get("accounts") instanceof JsonNode);
        // a field absent from the wire stays null (not manufactured)
        assertNull(r.get("manager_id"));
        assertNull(r.get("last_login"));
        assertNull(r.get("type"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("source_id", "7f0002"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("correlated", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("inactive", "false"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("is_workgroup", "true"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("name", "spadmin", "inactive", "false"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("name", "spadmin", "correlated", "true"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("bogus", "x"), null, null));
        // nested jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("accounts", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("7f0002", win.get(0).get("source_id"));
    }
}
