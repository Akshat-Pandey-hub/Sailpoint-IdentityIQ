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
 * Contract for the KF Agent Identity-Request-Approval read service: reuses the SHARED IdentityRequest
 * native import over a fake page source (no live IIQ, no DB), collecting only the nested approval rows;
 * returns our 17 DB-named fields, excludes the identityrequestapprovalid PK + lineage, serializes
 * comments/sign_off as JSON (array/object), keeps booleans/integers, preserves null, and supports generic
 * exact filtering on any scalar field. Also verifies empty→[].
 */
class NativeIdentityRequestApprovalRestServiceTest {

    private final NativeIdentityRequestApprovalRestService svc = new NativeIdentityRequestApprovalRestService();

    // valid 32-hex request id (canonicalRequestId requires a UUID-format sourceId; approvals do not)
    private static final String REQ = "7f00010198421229819849fb00000001";

    private static String approval1() {
        return "{\"requestSourceId\":\"" + REQ + "\",\"requestName\":\"REQ-001\",\"workItemId\":\"wi-1\","
                + "\"workItemType\":\"Approval\",\"owner\":\"spadmin\",\"ownerId\":\"u1\","
                + "\"completer\":\"spadmin\",\"approved\":true,\"state\":\"Finished\",\"stateKey\":\"finished\","
                + "\"typeKey\":\"approval\",\"approvalItemCount\":2,\"approvalIndex\":0,"
                + "\"comments\":[\"approved by manager\"],\"signOff\":{\"by\":\"spadmin\"},"
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.WorkflowSummary$ApprovalSummary\",\"extractionRunId\":\"run-1\"}";
    }

    private static String approval2() {
        return "{\"requestSourceId\":\"" + REQ + "\",\"requestName\":\"REQ-001\",\"workItemId\":\"wi-2\","
                + "\"approved\":false,\"state\":\"Pending\",\"approvalIndex\":1,"
                + "\"comments\":[],\"signOff\":{}}";
    }

    /** One request carrying two nested approvals (the shared import pulls approvals from the request's approvals[]). */
    private static NativeIdentityRequestPageSource source() {
        String req = "{\"sourceId\":\"" + REQ + "\",\"name\":\"REQ-001\",\"items\":[],"
                + "\"approvals\":[" + approval1() + "," + approval2() + "]}";
        return (start, limit) -> start == 0 ? "{\"rows\":[" + req + "]}" : "{\"rows\":[]}";
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
        assertEquals(REQ, r.get("request_source_id"));
        assertEquals("REQ-001", r.get("request_name"));
        assertEquals("wi-1", r.get("work_item_id"));
        assertEquals("spadmin", r.get("owner"));
        assertEquals(Boolean.TRUE, r.get("approved"));
        assertEquals("Finished", r.get("state"));
        assertEquals(Integer.valueOf(2), r.get("approval_item_count"));
        assertEquals(Integer.valueOf(0), r.get("approval_index"));
        assertEquals(17, r.size(), "exactly the 17 SailPoint-facing fields");

        assertFalse(r.containsKey("identityrequestapprovalid"));
        assertFalse(r.containsKey("source_id")); // the native approval has no business source id
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
    void commentsAndSignOffAreJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertTrue(a.get("comments") instanceof JsonNode, "comments is jsonb -> JSON array");
        assertTrue(((JsonNode) a.get("comments")).isArray());
        assertTrue(a.get("sign_off") instanceof JsonNode, "sign_off is jsonb -> JSON object");
        assertTrue(((JsonNode) a.get("sign_off")).isObject());

        Map<String, Object> b = rows.get(1);
        assertTrue(((JsonNode) b.get("comments")).isArray(), "empty [] comments preserved as JSON");
        assertEquals(0, ((JsonNode) b.get("comments")).size());
        assertNull(b.get("owner"), "unset field stays null");
        assertNull(b.get("approval_item_count"));
        assertNull(b.get("start_date"));
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(2, svc.fetch(source(), f("request_name", "REQ-001"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("work_item_id", "wi-1"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("owner", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("approved", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("approved", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("approval_index", "1"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("request_name", "REQ-001", "state", "Finished"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("request_name", "REQ-001", "state", "Nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("comments", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("sign_off", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("wi-2", win.get(0).get("work_item_id"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
