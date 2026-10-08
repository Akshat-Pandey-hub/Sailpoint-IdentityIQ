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
 * Contract for the KF Agent Identity-Request read service: reuses the native paging loop + parser over a
 * fake page source (no live IIQ, no DB), returns our 34 DB-named request fields, excludes the
 * identityrequestid PK + lineage, serializes {@code errors} as JSON, preserves null, handles booleans,
 * and supports generic exact filtering on any scalar field. Nested items/approvals are NOT part of this
 * response. Also verifies the empty case.
 */
class NativeIdentityRequestRestServiceTest {

    private final NativeIdentityRequestRestService svc = new NativeIdentityRequestRestService();

    private static String executing() {
        return "{\"sourceId\":\"7f00010198421229819849f900000001\",\"name\":\"REQ-001\",\"type\":\"AccessRequest\","
                + "\"userFriendlyType\":\"Access Request\",\"state\":\"Executing\",\"source\":\"LCM\","
                + "\"completionStatus\":\"Pending\",\"executionStatus\":\"Executing\",\"priority\":\"Normal\","
                + "\"requesterId\":\"u1\",\"requesterDisplayName\":\"spadmin\","
                + "\"targetId\":\"t1\",\"targetDisplayName\":\"Alexander Evans\","
                + "\"executing\":true,\"failure\":false,\"successful\":false,\"iiqOnly\":false,"
                + "\"provisioningComplete\":false,\"errors\":[\"could not reach target\"],"
                + "\"itemCount\":3,\"approvalCount\":1,"
                // lineage/technical fields — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.IdentityRequest\",\"extractionRunId\":\"run-1\"}";
    }

    private static String completed() {
        return "{\"sourceId\":\"7f00010198421229819849f900000002\",\"name\":\"REQ-002\",\"type\":\"AccessRequest\",\"state\":\"Completed\","
                + "\"completionStatus\":\"Success\",\"executing\":false,\"successful\":true,"
                + "\"itemCount\":1,\"approvalCount\":0}";
    }

    private static NativeIdentityRequestPageSource source() {
        return (start, limit) -> start == 0
                ? "{\"rows\":[" + executing() + "," + completed() + "]}"
                : "{\"rows\":[]}";
    }

    private static NativeIdentityRequestPageSource emptySource() {
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
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("7f00010198421229819849f900000001", r.get("source_id"));
        assertEquals("REQ-001", r.get("name"));
        assertEquals("Access Request", r.get("user_friendly_type"));
        assertEquals("Executing", r.get("state"));
        assertEquals("spadmin", r.get("requester_display_name"));
        assertEquals("Alexander Evans", r.get("target_display_name"));
        assertEquals(Boolean.TRUE, r.get("executing"));
        assertEquals(Boolean.FALSE, r.get("successful"));
        assertEquals(Integer.valueOf(3), r.get("item_count"));
        assertEquals(34, r.size(), "exactly the 34 SailPoint-facing fields");

        assertFalse(r.containsKey("identityrequestid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
        // nested items/approvals are a separate entity, not in this response
        assertFalse(r.containsKey("items"));
        assertFalse(r.containsKey("approvals"));
    }

    @Test
    void errorsIsJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertTrue(a.get("errors") instanceof JsonNode, "errors is jsonb -> JSON");
        assertTrue(((JsonNode) a.get("errors")).isArray());

        Map<String, Object> b = rows.get(1);
        assertNull(b.get("errors"), "missing errors stays null");
        assertNull(b.get("requester_display_name"), "unset field stays null");
        assertNull(b.get("target_id"));
    }

    @Test
    void genericScalarAndBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "REQ-001"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("type", "AccessRequest"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("completion_status", "Success"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("executing", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("successful", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("item_count", "3"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "AccessRequest", "state", "Completed"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "AccessRequest", "state", "Nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // errors jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("errors", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("7f00010198421229819849f900000002", win.get(0).get("source_id"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
