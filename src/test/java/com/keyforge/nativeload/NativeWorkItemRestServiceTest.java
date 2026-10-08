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
 * Contract for the KF Agent WorkItem read service: reuses the EXISTING native WorkItem import over a fake
 * page source (no live IIQ, no DB), collecting records into a non-persisting sink; returns our 41 DB-named
 * business fields, excludes the workitemid PK + lineage/soft-delete, serializes the four structured fields
 * (comments/sign_offs/owner_history/approval_set_items) as JSON while text/boolean/integer/timestamp keep
 * their native type, preserves null, and supports generic exact filtering on any scalar field. Mirrors the
 * two supplied DB sample rows (ManualAction RapidSetup Leaver work items).
 */
class NativeWorkItemRestServiceTest {

    private final NativeWorkItemRestService svc = new NativeWorkItemRestService();

    // 32-hex source ids are required by NativeWorkItemRepository.canonicalWorkItemId (ParquetIds.canonicalUuid).
    private static final String WI_39 = "7f0001019fbf1a17819fcd8d03fd1c25";
    private static final String WI_40 = "7f0001019fbf1a17819fcd8d067c1c33";

    /** Sample row 39: structured approval_set_items, empty-array comments/sign_offs/owner_history, counts 0, flags. */
    private static String workItem39() {
        return "{\"sourceId\":\"" + WI_39 + "\",\"name\":\"0000000039\",\"type\":\"ManualAction\",\"level\":\"Normal\","
                + "\"requesterId\":\"7f000101971416688197147684ad00ff\",\"requesterName\":\"spadmin\","
                + "\"ownerId\":\"7f000101971416688197147684ad00ff\",\"ownerName\":\"spadmin\","
                + "\"handler\":\"sailpoint.api.Workflower\","
                + "\"notificationName\":\"Manual Changes requested for User: Sophia Bennett\","
                + "\"identityRequestId\":\"7f0001019f061fdc819f9cdef3a6085a\",\"targetId\":\"App_IDJ0001005\","
                + "\"certificationRelated\":false,\"workflowCaseId\":\"7f0001019fbf1a17819fcd8d029a1c1a\","
                + "\"workflowCaseName\":\"RapidSetup Leaver FOR App_IDJ0001005 1785859989192\","
                + "\"escalationCount\":0,\"reminders\":0,\"remindersSent\":0,\"expired\":false,\"expirable\":true,"
                + "\"approvalSetItemCount\":1,\"comments\":[],\"signOffs\":[],\"ownerHistory\":[],"
                + "\"approvalSetItems\":[{\"owner\":null,\"state\":null,\"approver\":null,\"operation\":\"Disable\","
                + "\"displayValue\":null,\"applicationName\":\"FlatFile-HR-Source\"}],"
                + "\"created\":\"2026-08-04T16:13:18.717Z\",\"modified\":\"2026-08-04T16:13:18.754Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.WorkItem\",\"extractionRunId\":\"run-1\"}";
    }

    /** Sample row 40: like 39 but a different affected user/target; used to prove filtering + windowing. */
    private static String workItem40() {
        return "{\"sourceId\":\"" + WI_40 + "\",\"name\":\"0000000040\",\"type\":\"ManualAction\",\"level\":\"Normal\","
                + "\"requesterId\":\"7f000101971416688197147684ad00ff\",\"requesterName\":\"spadmin\","
                + "\"ownerId\":\"7f000101971416688197147684ad00ff\",\"ownerName\":\"spadmin\","
                + "\"handler\":\"sailpoint.api.Workflower\","
                + "\"notificationName\":\"Manual Changes requested for User: Liam Whitfield\","
                + "\"identityRequestId\":\"7f0001019f061fdc819f9cdef3bc0862\",\"targetId\":\"App_IDJ0001009\","
                + "\"certificationRelated\":false,\"workflowCaseId\":\"7f0001019fbf1a17819fcd8d05341c28\","
                + "\"workflowCaseName\":\"RapidSetup Leaver FOR App_IDJ0001009 1785859989530\","
                + "\"escalationCount\":0,\"reminders\":0,\"remindersSent\":0,\"expired\":false,\"expirable\":true,"
                + "\"approvalSetItemCount\":1,\"comments\":[],\"signOffs\":[],\"ownerHistory\":[],"
                + "\"approvalSetItems\":[{\"operation\":\"Disable\",\"applicationName\":\"FlatFile-HR-Source\"}],"
                + "\"created\":\"2026-08-04T16:13:19.356Z\",\"modified\":\"2026-08-04T19:39:09.081Z\"}";
    }

    private static NativeWorkItemPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + workItem39() + "," + workItem40() + "]}" : "{\"rows\":[]}";
    }

    private static NativeWorkItemPageSource emptySource() {
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
        assertEquals(WI_39, r.get("source_id"));
        assertEquals("0000000039", r.get("name"));
        assertEquals("ManualAction", r.get("type"));
        assertEquals("Normal", r.get("level"));
        assertEquals("spadmin", r.get("requester_name"));
        assertEquals("spadmin", r.get("owner_name"));
        assertEquals("sailpoint.api.Workflower", r.get("handler"));
        assertEquals("Manual Changes requested for User: Sophia Bennett", r.get("notification_name"));
        assertEquals("7f0001019f061fdc819f9cdef3a6085a", r.get("identity_request_id"));
        assertEquals("App_IDJ0001005", r.get("target_id"));
        assertEquals("RapidSetup Leaver FOR App_IDJ0001005 1785859989192", r.get("workflow_case_name"));
        assertEquals(41, r.size(), "exactly the 41 SailPoint-facing business fields");

        // excluded KeyForge/technical columns (PK is not a business field)
        assertFalse(r.containsKey("workitemid"));
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
    void nativeTypesBooleanNumericJsonTimestampAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);

        // booleans stay booleans
        assertEquals(Boolean.FALSE, r.get("certification_related"));
        assertEquals(Boolean.FALSE, r.get("expired"));
        assertEquals(Boolean.TRUE, r.get("expirable"));
        // integers stay integers
        assertEquals(Integer.valueOf(0), r.get("escalation_count"));
        assertEquals(Integer.valueOf(0), r.get("reminders"));
        assertEquals(Integer.valueOf(0), r.get("reminders_sent"));
        assertEquals(Integer.valueOf(1), r.get("approval_set_item_count"));
        // timestamps serialize as ISO strings
        assertEquals("2026-08-04T16:13:18.717Z", r.get("created_at"));
        assertEquals("2026-08-04T16:13:18.754Z", r.get("modified_at"));

        // the four structured fields stay JSON (never stringified)
        assertTrue(r.get("comments") instanceof JsonNode && ((JsonNode) r.get("comments")).isArray());
        assertTrue(r.get("sign_offs") instanceof JsonNode && ((JsonNode) r.get("sign_offs")).isArray());
        assertTrue(r.get("owner_history") instanceof JsonNode && ((JsonNode) r.get("owner_history")).isArray());
        Object asi = r.get("approval_set_items");
        assertTrue(asi instanceof JsonNode && ((JsonNode) asi).isArray());
        assertEquals("FlatFile-HR-Source", ((JsonNode) asi).get(0).get("applicationName").asText());

        // nulls preserved for unset scalar/timestamp fields
        assertNull(r.get("assignee_id"));
        assertNull(r.get("state"));
        assertNull(r.get("completer"));
        assertNull(r.get("certification_id"));
        assertNull(r.get("expiration"));
        assertNull(r.get("wake_up_date"));
    }

    @Test
    void genericBooleanNumericAndScalarFilters() {
        assertEquals(2, svc.fetch(source(), f("type", "ManualAction"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("target_id", "App_IDJ0001005"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("expirable", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("expired", "false"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("approval_set_item_count", "1"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("escalation_count", "0"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "manualaction"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("requester_name", "spadmin", "name", "0000000040"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("requester_name", "spadmin", "name", "nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // the four jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("comments", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("approval_set_items", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("0000000040", win.get(0).get("name"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("type", "ManualAction"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("name", "0000000039"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
