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
 * Contract for the KF Agent Workgroup read service: reuses the native Workgroup paging loop + parser over
 * a fake page source (no live IIQ, no DB), returns our DB-named SailPoint Workgroup fields, excludes the
 * workgroupid PK + lineage, serializes capabilities/attributes as JSON (not strings), preserves
 * null/empty, and supports generic exact filtering on any scalar response field — before paging.
 */
class NativeWorkgroupRestServiceTest {

    private final NativeWorkgroupRestService svc = new NativeWorkgroupRestService();

    /** Rows shaped like the plugin wire (NativeWorkgroupWire keys), mirroring the validation rows. */
    private static String adminq() {
        return "{\"sourceId\":\"7f00..110c\",\"name\":\"Adminq\",\"displayName\":\"Adminq\","
                + "\"displayableName\":\"Adminq\",\"email\":\"\",\"description\":\"\","
                + "\"notificationOption\":\"Both\",\"inactive\":false,\"workgroup\":true,"
                + "\"ownerId\":\"7f00..00ff\",\"ownerName\":\"spadmin\","
                + "\"capabilities\":[\"SystemAdministrator\",\"Auditor\"],"
                + "\"attributes\":{\"displayName\":\"Adminq\"},"
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Identity\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T00:00:00Z\"}";
    }

    private static String adminCap() {
        return "{\"sourceId\":\"7f00..110e\",\"name\":\"AdminCap\",\"displayName\":\"AdminCap\","
                + "\"displayableName\":\"AdminCap\",\"notificationOption\":\"Both\",\"inactive\":false,"
                + "\"workgroup\":true,\"ownerId\":\"7f00..00ff\",\"ownerName\":\"spadmin\","
                + "\"capabilities\":[],\"attributes\":{\"displayName\":\"AdminCap\"}}";
    }

    private static NativeWorkgroupPageSource source() {
        String envelope = "{\"entity\":\"Workgroup\",\"rows\":[" + adminq() + "," + adminCap() + "]}";
        String empty = "{\"entity\":\"Workgroup\",\"rows\":[]}";
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
        assertEquals("7f00..110c", r.get("source_id"));
        assertEquals("Adminq", r.get("name"));
        assertEquals("Both", r.get("notification_option"));
        assertEquals(Boolean.TRUE, r.get("is_workgroup"));
        assertEquals(Boolean.FALSE, r.get("inactive"));
        assertEquals("spadmin", r.get("owner_name"));
        assertEquals(16, r.size(), "exactly the SailPoint-facing Workgroup fields");

        assertFalse(r.containsKey("workgroupid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash")); // Workgroup has no source_hash
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void nestedJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> adminq = rows.get(0);
        assertTrue(adminq.get("capabilities") instanceof JsonNode, "capabilities is a JSON array");
        assertTrue(((JsonNode) adminq.get("capabilities")).isArray());
        assertTrue(adminq.get("attributes") instanceof JsonNode, "attributes is a JSON object");
        assertTrue(((JsonNode) adminq.get("attributes")).isObject());
        // empty capabilities array preserved
        assertTrue(((JsonNode) rows.get(1).get("capabilities")).isArray());
        assertEquals(0, ((JsonNode) rows.get(1).get("capabilities")).size());
        // empty-string email preserved as "", missing fields stay null
        assertEquals("", adminq.get("email"));
        assertNull(rows.get(1).get("email"), "missing email stays null");
        assertNull(adminq.get("type"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "Adminq"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("is_workgroup", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("inactive", "false"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("owner_name", "spadmin"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("name", "Nope"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("name", "AdminCap", "owner_name", "spadmin"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("name", "AdminCap", "owner_name", "nobody"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // nested jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("capabilities", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("7f00..110e", win.get(0).get("source_id"));
    }
}
