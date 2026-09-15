package com.keyforge.iiq.taskresult;

import com.keyforge.iiq.client.IiqApiClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCIM /TaskResults parsing + mapping. Verifies the verified field set, ISO-8601→UTC timestamp
 * parsing, messages preserved as raw JSON, null handling for absent fields, deterministic canonical
 * id (basis of upsert idempotency), and that a missing id is reported not invented.
 */
class TaskResultTest {

    /** Verbatim shape captured live (one full record + one sparse record). */
    private static final String JSON =
            "{\"totalResults\":2,\"Resources\":["
            + "{\"id\":\"7f0001019842122981984a0a088a0e80\",\"name\":\"Full Text Index Refresh\","
            + "\"type\":\"System\",\"taskDefinition\":\"Full Text Index Refresh\",\"completionStatus\":\"Error\","
            + "\"host\":\"keyforgeiga\",\"launcher\":\"spadmin\",\"launched\":\"2025-07-27T04:00:28.298Z\","
            + "\"completed\":\"2025-07-27T04:00:28.321Z\",\"partitioned\":false,\"terminated\":false,"
            + "\"pendingSignoffs\":0,\"messages\":[\"Missing service definition: FullText\"],"
            + "\"meta\":{\"created\":\"2025-07-27T04:00:28.298Z\"}},"
            + "{\"id\":\"7f0001019842122981984a0a088a0e81\",\"name\":\"Sparse\",\"type\":\"System\"}"
            + "]}";

    private static IiqApiClient fake(String body) {
        return new IiqApiClient() {
            @Override public String get(String path, Map<String, String> q) {
                assertEquals(TaskResultService.TASK_RESULTS_PATH, path);
                return body;
            }
        };
    }

    @Test
    void parsesTaskResultsIncludingVerifiedFields() {
        List<TaskResult> rs = new TaskResultService(fake(JSON)).getAllTaskResults();
        assertEquals(2, rs.size());
        TaskResult t = rs.get(0);
        assertEquals("Full Text Index Refresh", t.name());
        assertEquals("System", t.type());
        assertEquals("Error", t.completionStatus());
        assertEquals("keyforgeiga", t.host());
        assertEquals("spadmin", t.launcher());
        assertEquals(Boolean.FALSE, t.partitioned());
        assertEquals(Integer.valueOf(0), t.pendingSignoffs());
        assertEquals("[\"Missing service definition: FullText\"]", t.messagesJson());
    }

    @Test
    void mapsCanonicalIdRawIdAndUtcTimestamps() {
        TaskResult t = new TaskResultService(fake(JSON)).getAllTaskResults().get(0);
        TaskResultRow row = TaskResultRowMapper.map(t);
        assertEquals("7f000101-9842-1229-8198-4a0a088a0e80", row.taskresultid());
        assertEquals("7f0001019842122981984a0a088a0e80", row.sourceId());   // raw id preserved
        assertEquals("Error", row.completionStatus());
        assertEquals(LocalDateTime.of(2025, 7, 27, 4, 0, 28, 298_000_000), row.launched());
        assertEquals(LocalDateTime.of(2025, 7, 27, 4, 0, 28, 321_000_000), row.completed());
        assertEquals("[\"Missing service definition: FullText\"]", row.messagesJson());
    }

    @Test
    void sparseRecordLeavesAbsentFieldsNull() {
        TaskResult t = new TaskResultService(fake(JSON)).getAllTaskResults().get(1);
        TaskResultRow row = TaskResultRowMapper.map(t);
        assertEquals("Sparse", row.name());
        assertNull(row.completionStatus());
        assertNull(row.launcher());
        assertNull(row.launched());
        assertNull(row.completed());
        assertNull(row.partitioned());
        assertNull(row.pendingSignoffs());
        assertNull(row.messagesJson());   // no messages array → NULL (not "[]")
    }

    @Test
    void emptyResultYieldsNoTaskResults() {
        assertTrue(new TaskResultService(fake("{\"totalResults\":0,\"Resources\":[]}"))
                .getAllTaskResults().isEmpty());
    }

    @Test
    void canonicalIdIsDeterministicForIdempotentUpsert() {
        // Same source id → same PK on every run → ON CONFLICT (taskresultid) updates, never duplicates.
        String id = "7f0001019842122981984a0a088a0e80";
        assertEquals(TaskResultRowMapper.toCanonicalUuid(id), TaskResultRowMapper.toCanonicalUuid(id));
        assertEquals("7f000101-9842-1229-8198-4a0a088a0e80", TaskResultRowMapper.toCanonicalUuid(id));
    }

    @Test
    void missingIdIsReportedNotInvented() {
        TaskResult bad = new TaskResult(null, "x", "System", "def", "Success",
                "h", "u", null, null, false, false, 0, null);
        assertThrows(TaskResultMappingException.class, () -> TaskResultRowMapper.map(bad));
    }
}
