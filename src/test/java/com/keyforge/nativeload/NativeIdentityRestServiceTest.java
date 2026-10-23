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

    /** Fake source that honours start/limit like the plugin (firstRow/resultLimit over a fixed order). */
    private static NativeIdentityPageSource source() {
        List<String> rows = List.of(spadmin(), evans());
        return (start, limit) -> {
            int from = Math.min(Math.max(0, start), rows.size());
            int to = Math.min(rows.size(), from + (limit <= 0 ? 100 : limit));
            return "{\"entity\":\"Identity\",\"limit\":" + limit + ",\"rows\":["
                    + String.join(",", rows.subList(from, to)) + "]}";
        };
    }

    /** Plugin-like fake over {@code size} identities "id00000".., recording every (start, limit) call. */
    private static final class RecordingSource implements NativeIdentityPageSource {
        final int size;
        final List<int[]> calls = new java.util.ArrayList<>();

        RecordingSource(int size) {
            this.size = size;
        }

        @Override
        public String fetchPage(int start, int limit) {
            calls.add(new int[]{start, limit});
            int effective = limit <= 0 ? 100 : Math.min(limit, 1000);
            StringBuilder rows = new StringBuilder();
            for (int i = start; i < Math.min(size, start + effective); i++) {
                if (rows.length() > 0) {
                    rows.append(',');
                }
                rows.append("{\"sourceId\":\"").append(id(i)).append("\",\"name\":\"user").append(i)
                        .append("\",\"inactive\":").append(i % 2 == 0).append('}');
            }
            return "{\"entity\":\"Identity\",\"limit\":" + effective + ",\"rows\":[" + rows + "]}";
        }

        int rowsRead() {
            int n = 0;
            for (int[] c : calls) {
                n += Math.max(0, Math.min(size, c[0] + Math.min(c[1], 1000)) - c[0]);
            }
            return n;
        }

        static String id(int i) {
            return String.format("id%05d", i);
        }
    }

    private static List<Object> ids(List<Map<String, Object>> rows) {
        List<Object> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.get("source_id")));
        return out;
    }

    @Test
    void limitReadsOnlyThatWindowFromThePlugin() {
        RecordingSource plugin = new RecordingSource(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, null, 10);

        assertEquals(10, rows.size());
        assertEquals(RecordingSource.id(0), rows.get(0).get("source_id"));
        assertEquals(1, plugin.calls.size(), "one plugin call, not a scan of 10,000");
        assertEquals(0, plugin.calls.get(0)[0]);
        assertEquals(10, plugin.calls.get(0)[1]);
    }

    @Test
    void startAndLimitArePassedToThePlugin() {
        RecordingSource plugin = new RecordingSource(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 1, 2);

        assertEquals(List.of(RecordingSource.id(1), RecordingSource.id(2)), ids(rows));
        assertEquals(1, plugin.calls.size());
        assertEquals(1, plugin.calls.get(0)[0], "plugin start=1");
        assertEquals(2, plugin.calls.get(0)[1], "plugin limit=2");
    }

    @Test
    void startWithoutLimitNeverReadsTheSkippedRows() {
        RecordingSource plugin = new RecordingSource(2_500);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 2_000, null);

        assertEquals(500, rows.size());
        assertEquals(RecordingSource.id(2_000), rows.get(0).get("source_id"));
        assertEquals(2_000, plugin.calls.get(0)[0], "first plugin call starts at the offset");
        assertEquals(500, plugin.rowsRead());
    }

    @Test
    void filteredRequestStopsOnceTheWindowIsFilled() {
        RecordingSource plugin = new RecordingSource(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, f("name", "user1234"), null, 1);

        assertEquals(List.of(RecordingSource.id(1_234)), ids(rows));
        assertEquals(3, plugin.calls.size(), "pages 0, 500, 1000 only");
        assertTrue(plugin.rowsRead() < 10_000);

        RecordingSource paged = new RecordingSource(3_000);
        List<Map<String, Object>> inactive = svc.fetch(paged, f("inactive", "true"), 2, 3);
        assertEquals(List.of(RecordingSource.id(4), RecordingSource.id(6), RecordingSource.id(8)), ids(inactive),
                "filter first, then start/limit over the matches");
    }

    @Test
    void noParametersStillReturnsEveryIdentity() {
        RecordingSource plugin = new RecordingSource(1_234);
        assertEquals(1_234, svc.fetch(plugin, null, null, null).size());
        assertEquals(1_234, plugin.rowsRead());
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
