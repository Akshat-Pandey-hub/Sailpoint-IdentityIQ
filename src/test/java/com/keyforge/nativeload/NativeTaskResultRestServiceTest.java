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
 * Contract for the KF Agent Task-Result read service: reuses the EXISTING native import over a fake page
 * source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 26 DB-named
 * business fields, excludes the taskresultid PK + lineage/soft-delete, keeps the native types (progress text,
 * percent_complete/run_length/pending_signoffs integer, partitioned/terminate_requested/complete boolean, the
 * six ISO timestamps) and the two structured fields (messages/attributes) as real JSON, preserves null, and
 * supports generic exact filtering on scalar fields only. Mirrors the Error (with messages) + Success (with
 * stats attributes) samples.
 */
class NativeTaskResultRestServiceTest {

    private final NativeTaskResultRestService svc = new NativeTaskResultRestService();

    private static final String FULLTEXT = "7f0001019842122981984a0a088a0e80";
    private static final String GROUPS = "7f0001019842122981984a0a53520e82";

    /** Sample 1: Full Text Index Refresh — Error, a messages array, empty attributes, null targets/verified. */
    private static String fullText() {
        return "{\"sourceId\":\"" + FULLTEXT + "\",\"name\":\"Full Text Index Refresh\",\"type\":\"System\","
                + "\"completionStatus\":\"Error\",\"definitionName\":\"Full Text Index Refresh\","
                + "\"launcher\":\"spadmin\",\"host\":\"keyforgeiga\","
                + "\"schedule\":\"54c4fda028c842b3857cdd8ef8e9c44e\","
                + "\"percentComplete\":0,\"runLength\":0,\"pendingSignoffs\":0,"
                + "\"partitioned\":false,\"terminateRequested\":true,\"complete\":true,"
                + "\"launched\":\"2025-07-27T04:00:28.298Z\",\"completed\":\"2025-07-27T04:00:28.321Z\","
                + "\"messages\":[{\"key\":\"Missing service definition: FullText\",\"type\":\"Error\"}],"
                + "\"attributes\":{},"
                + "\"created\":\"2025-07-27T04:00:28.298Z\",\"modified\":\"2025-07-27T04:00:28.321Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.TaskResult\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:35.238Z\"}";
    }

    /** Sample 2: Refresh Groups — Success, empty messages, a populated statistics attributes object. */
    private static String refreshGroups() {
        return "{\"sourceId\":\"" + GROUPS + "\",\"name\":\"Refresh Groups\",\"type\":\"Identity\","
                + "\"completionStatus\":\"Success\",\"definitionName\":\"Refresh Groups\","
                + "\"launcher\":\"spadmin\",\"host\":\"keyforgeiga\","
                + "\"schedule\":\"0885b246ffe6423eaa61f43238720d39\","
                + "\"progress\":\"done\",\"percentComplete\":100,\"runLength\":0,\"pendingSignoffs\":0,"
                + "\"partitioned\":false,\"terminateRequested\":true,\"complete\":true,"
                + "\"launched\":\"2025-07-27T04:00:47.442Z\",\"completed\":\"2025-07-27T04:00:47.596Z\","
                + "\"messages\":[],"
                + "\"attributes\":{\"total\":\"14\",\"groupsCreated\":\"0\",\"groupsIndexed\":\"0\"},"
                + "\"created\":\"2025-07-27T04:00:47.442Z\",\"modified\":\"2025-07-27T04:00:47.597Z\"}";
    }

    private static NativeTaskResultPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + fullText() + "," + refreshGroups() + "]}" : "{\"rows\":[]}";
    }

    private static NativeTaskResultPageSource emptySource() {
        return (start, limit) -> "{\"rows\":[]}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsTwentySixFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(FULLTEXT, r.get("source_id"));
        assertEquals("Full Text Index Refresh", r.get("name"));
        assertEquals("System", r.get("type"));
        assertEquals("Error", r.get("completion_status"));
        assertEquals("spadmin", r.get("launcher"));
        assertEquals("keyforgeiga", r.get("host"));
        assertEquals("54c4fda028c842b3857cdd8ef8e9c44e", r.get("schedule"));
        assertEquals(26, r.size(), "exactly the 26 SailPoint-facing business fields");

        assertFalse(r.containsKey("taskresultid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void nativeTypesStructuredJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> ft = rows.get(0);

        // integers stay integers
        assertEquals(Integer.valueOf(0), ft.get("percent_complete"));
        assertEquals(Integer.valueOf(0), ft.get("run_length"));
        assertEquals(Integer.valueOf(0), ft.get("pending_signoffs"));
        // booleans stay booleans (complete is BOOLEAN, not a timestamp)
        assertEquals(Boolean.FALSE, ft.get("partitioned"));
        assertEquals(Boolean.TRUE, ft.get("terminate_requested"));
        assertEquals(Boolean.TRUE, ft.get("complete"));
        // timestamps serialize as ISO strings
        assertEquals("2025-07-27T04:00:28.298Z", ft.get("launched_at"));
        assertEquals("2025-07-27T04:00:28.321Z", ft.get("completed_at"));
        assertEquals("2025-07-27T04:00:28.298Z", ft.get("created_at"));

        // messages structured JSON array (not stringified)
        Object messages = ft.get("messages");
        assertTrue(messages instanceof JsonNode && ((JsonNode) messages).isArray());
        assertEquals("Error", ((JsonNode) messages).get(0).get("type").asText());
        // attributes structured JSON object (empty {} preserved)
        Object attrs = ft.get("attributes");
        assertTrue(attrs instanceof JsonNode && ((JsonNode) attrs).isObject());
        assertEquals(0, ((JsonNode) attrs).size());

        // null preservation
        assertNull(ft.get("target_name"));
        assertNull(ft.get("target_class"));
        assertNull(ft.get("target_id"));
        assertNull(ft.get("progress"), "progress is text and unset here");
        assertNull(ft.get("expiration_at"));
        assertNull(ft.get("verified_at"));

        // second row: progress is TEXT ("done"), attributes populated
        Map<String, Object> rg = rows.get(1);
        assertEquals("done", rg.get("progress"));
        assertEquals(Integer.valueOf(100), rg.get("percent_complete"));
        assertEquals("14", ((JsonNode) rg.get("attributes")).get("total").asText());
        assertEquals(0, ((JsonNode) rg.get("messages")).size());
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", FULLTEXT), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "Refresh Groups"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "System"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("completion_status", "Error"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("completion_status", "Success"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("launcher", "spadmin"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("host", "keyforgeiga"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("complete", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("partitioned", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("percent_complete", "100"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("progress", "done"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "system"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("launcher", "spadmin", "completion_status", "Error"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("launcher", "spadmin", "completion_status", "Pending"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("taskresultid", "x"), null, null));
        // the two jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("messages", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("attributes", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("Refresh Groups", win.get(0).get("name"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("launcher", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("launcher", "spadmin"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
