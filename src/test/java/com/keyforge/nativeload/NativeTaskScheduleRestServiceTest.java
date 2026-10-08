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
 * Contract for the KF Agent Task-Schedule read service: reuses the EXISTING native import over a fake page
 * source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 18 DB-named
 * business fields, excludes the taskscheduleid PK + lineage/soft-delete, keeps delete_on_finish boolean / the
 * six ISO timestamps / the two structured fields (cron_expressions/arguments) as real JSON, preserves null,
 * and supports generic exact filtering on scalar fields only. Mirrors the two supplied daily cron schedules
 * whose source_id is the schedule name (TaskSchedule has no GUID).
 */
class NativeTaskScheduleRestServiceTest {

    private final NativeTaskScheduleRestService svc = new NativeTaskScheduleRestService();

    private static final String EXPIRED = "Check expired work items daily";
    private static final String SUNSET = "Check sunset requests for notifications daily";

    /** Sample 1: expired-work-items daily — source_id = name, cron array + arguments object, null state/launcher. */
    private static String expired() {
        return "{\"sourceId\":\"" + EXPIRED + "\",\"name\":\"" + EXPIRED + "\","
                + "\"description\":\"Check for expired work items every day at midnight.\","
                + "\"definitionName\":\"Check Expired Work Items\",\"deleteOnFinish\":true,"
                + "\"lastExecution\":\"2026-10-05T00:00:00Z\",\"nextExecution\":\"2026-10-06T00:00:00Z\","
                + "\"nextActualExecution\":\"2026-10-06T00:00:00Z\","
                + "\"cronExpressions\":[\"0 0 0 * * ?\"],"
                + "\"arguments\":{\"executor\":\"Check Expired Work Items\",\"nextActualFireTime\":\"1791244800000\"},"
                + "\"created\":\"2026-10-04T00:00:00Z\",\"modified\":\"2026-10-05T00:00:00Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.TaskSchedule\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:39.534Z\"}";
    }

    /** Sample 2: sunset-requests daily — a second schedule used for filtering + windowing. */
    private static String sunset() {
        return "{\"sourceId\":\"" + SUNSET + "\",\"name\":\"" + SUNSET + "\","
                + "\"description\":\"Check for Roles and Entitlements that are about to expire and send a notification every day at midnight\","
                + "\"definitionName\":\"Check Sunset Requests\",\"deleteOnFinish\":true,"
                + "\"lastExecution\":\"2026-10-05T00:00:00Z\",\"nextExecution\":\"2026-10-06T00:00:00Z\","
                + "\"nextActualExecution\":\"2026-10-06T00:00:00Z\","
                + "\"cronExpressions\":[\"0 0 0 * * ?\"],"
                + "\"arguments\":{\"executor\":\"Check Sunset Requests\",\"nextActualFireTime\":\"1791244800000\"}}";
    }

    private static NativeTaskSchedulePageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + expired() + "," + sunset() + "]}" : "{\"rows\":[]}";
    }

    private static NativeTaskSchedulePageSource emptySource() {
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
    void returnsEighteenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(EXPIRED, r.get("source_id"), "source_id is the schedule name (no GUID)");
        assertEquals(EXPIRED, r.get("name"));
        assertEquals("Check for expired work items every day at midnight.", r.get("description"));
        assertEquals("Check Expired Work Items", r.get("definition_name"));
        assertEquals(18, r.size(), "exactly the 18 SailPoint-facing business fields");

        assertFalse(r.containsKey("taskscheduleid"));
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
    void booleanTimestampTypesStructuredJsonAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);

        // boolean stays boolean
        assertEquals(Boolean.TRUE, r.get("delete_on_finish"));
        // timestamps serialize as ISO strings
        assertEquals("2026-10-05T00:00:00Z", r.get("last_execution_at"));
        assertEquals("2026-10-06T00:00:00Z", r.get("next_execution_at"));
        assertEquals("2026-10-06T00:00:00Z", r.get("next_actual_execution_at"));
        assertEquals("2026-10-04T00:00:00Z", r.get("created_at"));

        // cron_expressions is a JSON array (not stringified)
        Object cron = r.get("cron_expressions");
        assertTrue(cron instanceof JsonNode && ((JsonNode) cron).isArray());
        assertEquals("0 0 0 * * ?", ((JsonNode) cron).get(0).asText());
        // arguments is a JSON object
        Object args = r.get("arguments");
        assertTrue(args instanceof JsonNode && ((JsonNode) args).isObject());
        assertEquals("Check Expired Work Items", ((JsonNode) args).get("executor").asText());

        // null optional fields preserved
        assertNull(r.get("state"));
        assertNull(r.get("new_state"));
        assertNull(r.get("launcher"));
        assertNull(r.get("host"));
        assertNull(r.get("last_launch_error"));
        assertNull(r.get("resume_at"));
    }

    @Test
    void genericScalarAndBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", EXPIRED), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", SUNSET), null, null).size());
        assertEquals(1, svc.fetch(source(), f("definition_name", "Check Expired Work Items"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("delete_on_finish", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("delete_on_finish", "false"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("next_execution_at", "2026-10-06T00:00:00Z"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("definition_name", "check expired work items"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("delete_on_finish", "true", "definition_name", "Check Sunset Requests"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("delete_on_finish", "true", "definition_name", "Nope"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("taskscheduleid", "x"), null, null));
        // the two jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("cron_expressions", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("arguments", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(SUNSET, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("delete_on_finish", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("delete_on_finish", "true"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
