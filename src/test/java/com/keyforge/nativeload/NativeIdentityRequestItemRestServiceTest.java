package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for the KF Agent Identity-Request-Item read service: reuses the SHARED IdentityRequest native
 * import over a fake page source (no live IIQ, no DB), collecting only the nested item rows; returns our
 * 33 DB-named scalar fields, excludes the identityrequestitemid PK + lineage, keeps booleans/integers as
 * JSON types, preserves null, and supports generic exact filtering on any field. Also verifies empty→[].
 */
class NativeIdentityRequestItemRestServiceTest {

    private final NativeIdentityRequestItemRestService svc = new NativeIdentityRequestItemRestService();

    // valid 32-hex ids (canonicalRequestId/canonicalItemId require UUID-format sourceIds)
    private static final String REQ = "7f00010198421229819849fa00000001";
    private static final String I1 = "7f00010198421229819849fa000000a1";
    private static final String I2 = "7f00010198421229819849fa000000a2";

    private static String item1() {
        return "{\"sourceId\":\"" + I1 + "\",\"requestSourceId\":\"" + REQ + "\",\"requestName\":\"REQ-001\","
                + "\"application\":\"EntraTarget\",\"attributeName\":\"groups\","
                + "\"attributeValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\",\"operation\":\"Add\","
                + "\"approverName\":\"spadmin\",\"approvalState\":\"Finished\",\"approved\":true,"
                + "\"approvalComplete\":true,\"rejected\":false,\"provisioningState\":\"Committed\","
                + "\"provisioningComplete\":true,\"provisioningFailed\":false,\"expansion\":false,"
                + "\"retries\":0,\"iiq\":false,"
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.IdentityRequestItem\",\"extractionRunId\":\"run-1\"}";
    }

    private static String item2() {
        return "{\"sourceId\":\"" + I2 + "\",\"requestSourceId\":\"" + REQ + "\",\"requestName\":\"REQ-001\","
                + "\"application\":\"Workday\",\"attributeName\":\"role\",\"attributeValue\":\"Viewer\","
                + "\"operation\":\"Add\",\"approved\":false,\"provisioningState\":\"Pending\",\"retries\":1}";
    }

    /** One request carrying two nested items (the shared import pulls items from the request's items[]). */
    private static NativeIdentityRequestPageSource source() {
        String req = "{\"sourceId\":\"" + REQ + "\",\"name\":\"REQ-001\",\"items\":[" + item1() + "," + item2() + "]}";
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
        assertEquals(I1, r.get("source_id"));
        assertEquals(REQ, r.get("request_source_id"));
        assertEquals("REQ-001", r.get("request_name"));
        assertEquals("EntraTarget", r.get("application"));
        assertEquals("groups", r.get("attribute_name"));
        assertEquals("Add", r.get("operation"));
        assertEquals(Boolean.TRUE, r.get("approved"));
        assertEquals(Boolean.FALSE, r.get("rejected"));
        assertEquals(Integer.valueOf(0), r.get("retries"));
        assertEquals(33, r.size(), "exactly the 33 SailPoint-facing fields");

        assertFalse(r.containsKey("identityrequestitemid"));
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
    void typesAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertEquals(Boolean.TRUE, a.get("provisioning_complete"));
        assertEquals(Integer.valueOf(0), a.get("retries"));

        Map<String, Object> b = rows.get(1);
        assertEquals(Boolean.FALSE, b.get("approved"));
        assertEquals(Integer.valueOf(1), b.get("retries"));
        assertNull(b.get("approver_name"), "unset field stays null");
        assertNull(b.get("approval_state"));
        assertNull(b.get("start_date"));
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("application", "EntraTarget"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("request_name", "REQ-001"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("operation", "Add"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("approved", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("approved", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("retries", "1"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("request_name", "REQ-001", "application", "Workday"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("request_name", "REQ-001", "application", "Nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("identityrequestitemid", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(I2, win.get(0).get("source_id"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
