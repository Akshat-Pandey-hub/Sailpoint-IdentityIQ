package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the KF Agent SyslogEvent read service ({@code /kfagent/syslog-events}). It reuses the
 * native {@link NativeSyslogEventImportService} + parser over a fake {@link NativeSyslogEventPageSource}
 * (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns the 11 native business
 * fields (including the newly-added {@code classname}), preserves nulls, keeps {@code created_at} as an
 * ISO-8601 string, excludes lineage/technical columns, and supports generic exact filtering with AND. The
 * field-count assertion fails if any genuine field is accidentally omitted or a technical one leaks in.
 */
class NativeSyslogEventRestServiceTest {

    /** The 11 kf_syslog_event business columns, in response order. */
    private static final List<String> COLUMNS = List.of(
            "source_id", "quick_key", "event_level", "server", "username", "thread", "classname",
            "line_number", "message", "stacktrace", "created_at");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NativeSyslogEventRestService svc = new NativeSyslogEventRestService();

    /** One native wire row (camelCase keys, matching NativeSyslogEventFields + the plugin wire). */
    private static Map<String, Object> wireRow(String id, String level, String server, String user,
                                               String thread, String classname, String line,
                                               String message, String created) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sourceId", id);
        r.put("quickKey", "qk-" + id);
        r.put("eventLevel", level);
        r.put("server", server);
        r.put("username", user);
        r.put("thread", thread);
        r.put("classname", classname);
        r.put("lineNumber", line);
        r.put("message", message);
        r.put("stacktrace", null);          // model a null column
        r.put("created", created);          // ISO-8601
        // lineage — must NOT appear in the REST response
        r.put("srcSystem", "IdentityIQ");
        r.put("srcInterface", "native_iiq_java_api");
        r.put("srcObjectType", "sailpoint.object.SyslogEvent");
        r.put("extractionRunId", "run-1");
        r.put("extractedAt", "2026-10-09T00:00:00Z");
        return r;
    }

    /** Build the plugin envelope {sourceCount, rows:[...]} the import service expects. */
    private static String envelope(List<Map<String, Object>> rows) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("entity", "SyslogEvent");
        env.put("sourceCount", rows.size());
        env.put("returned", rows.size());
        env.put("rows", rows);
        try {
            return MAPPER.writeValueAsString(env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** A single-page source: page 0 returns all rows (< internal page size → loop ends); later pages empty. */
    private static NativeSyslogEventPageSource source(List<Map<String, Object>> rows) {
        String full = envelope(rows);
        String empty = envelope(List.of());
        return (start, limit) -> start == 0 ? full : empty;
    }

    private static List<Map<String, Object>> sample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(wireRow("1", "ERROR", "host-a", "spadmin", "http-1",
                "sailpoint.api.Aggregator", "412", "boom", "2026-01-01T10:00:00Z"));
        rows.add(wireRow("2", "WARN", "host-a", "spadmin", "http-2",
                "sailpoint.api.Provisioner", "88", "slow", "2026-01-02T10:00:00Z"));
        rows.add(wireRow("3", "ERROR", "host-b", "system", "quartz-1",
                "sailpoint.task.ScanTask", "15", "failed", "2026-01-03T10:00:00Z"));
        return rows;
    }

    @Test
    void returnsElevenNativeFieldsIncludingClassnameAndExcludesLineage() {
        List<Map<String, Object>> rows = svc.fetch(source(sample()), null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(COLUMNS, new ArrayList<>(r.keySet()), "exactly the 11 fields in order");
        assertEquals(11, r.size());
        assertEquals("1", r.get("source_id"));
        assertEquals("ERROR", r.get("event_level"));
        assertEquals("host-a", r.get("server"));
        assertEquals("spadmin", r.get("username"));
        assertEquals("http-1", r.get("thread"));
        assertEquals("sailpoint.api.Aggregator", r.get("classname"), "the newly-added classname field");
        assertEquals("412", r.get("line_number"));
        assertEquals("boom", r.get("message"));
        assertEquals("2026-01-01T10:00:00Z", r.get("created_at"), "created kept as ISO-8601 string");

        // null column preserved and present
        assertTrue(r.containsKey("stacktrace"));
        assertNull(r.get("stacktrace"), "null stacktrace preserved");

        // technical / lineage columns excluded
        assertFalse(r.containsKey("syslogid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("srcSystem"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void scalarFiltersIncludingClassnameAndAndCombine() {
        NativeSyslogEventPageSource src = source(sample());
        assertEquals(2, svc.fetch(src, Map.of("event_level", "ERROR"), null, null).size());
        assertEquals(1, svc.fetch(src, Map.of("classname", "sailpoint.api.Provisioner"), null, null).size());
        assertEquals(2, svc.fetch(src, Map.of("server", "host-a"), null, null).size());
        // AND across two columns: ERROR on host-a -> only row 1
        assertEquals(1, svc.fetch(src, Map.of("event_level", "ERROR", "server", "host-a"), null, null).size());
        assertEquals(0, svc.fetch(src, Map.of("event_level", "WARN", "server", "host-b"), null, null).size());
        // case-sensitive
        assertEquals(0, svc.fetch(src, Map.of("event_level", "error"), null, null).size());
    }

    @Test
    void unknownAndTechnicalFilterNamesRejected() {
        NativeSyslogEventPageSource src = source(sample());
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(src, Map.of("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(src, Map.of("syslogid", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(src, Map.of("record_hash", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(src, Map.of("extraction_run_id", "x"), null, null));
    }

    @Test
    void filteringHappensBeforePaging() {
        NativeSyslogEventPageSource src = source(sample());
        // filter ERROR -> rows 1,3 ; window start=1,limit=1 -> row 3
        List<Map<String, Object>> win = svc.fetch(src, Map.of("event_level", "ERROR"), 1, 1);
        assertEquals(1, win.size());
        assertEquals("3", win.get(0).get("source_id"));
    }

    @Test
    void defaultExplicitAndInvalidPaging() {
        NativeSyslogEventPageSource src = source(sample());
        assertEquals(3, svc.fetch(src, null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(src, null, 1, 5);
        assertEquals(2, win.size());
        assertEquals("2", win.get(0).get("source_id"));
        assertEquals(1, svc.fetch(src, null, 0, 1).size());
        assertEquals(3, svc.fetch(src, null, -5, null).size());  // start<0 -> 0
        assertEquals(3, svc.fetch(src, null, null, -1).size());  // limit<0 -> all
        assertEquals(0, svc.fetch(src, null, 99, 5).size());     // past end -> []
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(source(List.of()), null, null, null));
    }

    @Test
    void restPathHasNoPostgresDependency() {
        // entire flow runs through an in-memory fake page source; no java.sql.Connection anywhere.
        assertEquals(3, svc.fetch(source(sample()), null, null, null).size());
    }
}
