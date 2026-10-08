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
 * Contract for the KF Agent Provisioning-Transaction read service: reuses the SHARED native
 * ProvisioningTransaction import over a fake page source (no live IIQ, no DB), collecting only the
 * TRANSACTION rows (never the derived items); returns our 34 DB-named business fields, excludes the
 * provisioningtxnid PK + lineage/soft-delete, serializes plan_result_errors as JSON while text stays text,
 * keeps forced/timed_out/filtered boolean + retry_count/item_count integer + timestamps ISO, preserves null,
 * and supports generic exact filtering on any scalar field. Mirrors the two supplied DB sample rows.
 */
class NativeProvisioningTransactionRestServiceTest {

    private final NativeProvisioningTransactionRestService svc = new NativeProvisioningTransactionRestService();

    private static final String TXN_A = "7f0001019f061fdc819fa50301271752";
    private static final String TXN_B = "7f0001019fa71124819fb3cc9bed170f";

    /** Sample A: forced=true, source=LCM, retry_count 0, item_count 6, a plan_result_errors array, + a nested item. */
    private static String txnA() {
        return "{\"sourceId\":\"" + TXN_A + "\",\"name\":\"0000000001\",\"operation\":\"Create\",\"type\":\"Auto\","
                + "\"status\":\"Failed\",\"source\":\"LCM\",\"integration\":\"corp directory\",\"forced\":true,"
                + "\"identityName\":\"App_IDJ0001002\",\"identityDisplayName\":\"Emma Coleman\","
                + "\"applicationName\":\"corp directory\",\"accountRequestOperation\":\"Create\","
                + "\"retryCount\":0,\"timedOut\":false,\"filtered\":false,"
                + "\"planResultErrors\":[{\"code\":\"X\",\"text\":\"boom\"}],\"itemCount\":6,"
                + "\"created\":\"2026-01-01T00:00:00Z\",\"modified\":\"2026-01-02T00:00:00Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.ProvisioningTransaction\",\"extractionRunId\":\"run-1\","
                // a nested derived item — must NOT leak into the transaction response
                + "\"items\":[{\"txnSourceId\":\"" + TXN_A + "\",\"itemType\":\"ATTRIBUTE\",\"name\":\"dn\","
                + "\"value\":\"uid=App_IDJ0001002\",\"itemIndex\":1}]}";
    }

    /** Sample B: forced=false, source null, retry_count null, item_count 8, no plan_result_errors. */
    private static String txnB() {
        return "{\"sourceId\":\"" + TXN_B + "\",\"name\":\"0000000002\",\"operation\":\"Create\",\"type\":\"Auto\","
                + "\"status\":\"Failed\",\"integration\":\"corp directory-LDAP-Target\",\"forced\":false,"
                + "\"identityName\":\"App_IDJ0001002\",\"identityDisplayName\":\"Emma Coleman\","
                + "\"nativeIdentity\":\"App_IDJ0001002\",\"accountDisplayName\":\"null null\","
                + "\"accountRequestOperation\":\"Create\",\"itemCount\":8,"
                + "\"created\":\"2026-02-01T00:00:00Z\",\"modified\":\"2026-02-02T00:00:00Z\"}";
    }

    /** Two transactions; the import breaks after the first short page (<internal page size). */
    private static NativeProvisioningTxnPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + txnA() + "," + txnB() + "]}" : "{\"rows\":[]}";
    }

    private static NativeProvisioningTxnPageSource emptySource() {
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
    void returnsOurFieldsAndExcludesKeyforgeAndItemFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size(), "only transactions, never the derived items");

        Map<String, Object> r = rows.get(0);
        assertEquals(TXN_A, r.get("source_id"));
        assertEquals("0000000001", r.get("name"));
        assertEquals("Create", r.get("operation"));
        assertEquals("Auto", r.get("type"));
        assertEquals("Failed", r.get("status"));
        assertEquals("LCM", r.get("source"));
        assertEquals("corp directory", r.get("integration"));
        assertEquals("App_IDJ0001002", r.get("identity_name"));
        assertEquals("Emma Coleman", r.get("identity_display_name"));
        assertEquals("corp directory", r.get("application_name"));
        assertEquals("Create", r.get("account_request_operation"));
        assertEquals(34, r.size(), "exactly the 34 SailPoint-facing business fields");

        // excluded KeyForge/technical columns
        assertFalse(r.containsKey("provisioningtxnid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
        // no derived-item fields leak into the transaction response
        assertFalse(r.containsKey("item_type"));
        assertFalse(r.containsKey("value"));
        assertFalse(r.containsKey("value_json"));
        assertFalse(r.containsKey("permission_target"));
        assertFalse(r.containsKey("item_index"));
        assertFalse(r.containsKey("items"));
    }

    @Test
    void nativeTypesBooleanNumericJsonTimestampAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertEquals(Boolean.TRUE, a.get("forced"));
        assertEquals(Boolean.FALSE, a.get("timed_out"));
        assertEquals(Boolean.FALSE, a.get("filtered"));
        assertEquals(Integer.valueOf(0), a.get("retry_count"));
        assertEquals(Integer.valueOf(6), a.get("item_count"));
        assertTrue(a.get("plan_result_errors") instanceof JsonNode, "plan_result_errors is jsonb -> JSON");
        assertTrue(((JsonNode) a.get("plan_result_errors")).isArray());
        assertEquals("2026-01-01T00:00:00Z", a.get("created_at"));
        assertEquals("2026-01-02T00:00:00Z", a.get("modified_at"));
        assertNull(a.get("certification_id"), "unset text field stays null");
        assertNull(a.get("last_retry"), "unset timestamp stays null");

        Map<String, Object> b = rows.get(1);
        assertEquals(Boolean.FALSE, b.get("forced"));
        assertEquals(Integer.valueOf(8), b.get("item_count"));
        assertEquals("null null", b.get("account_display_name"), "literal text preserved, not treated as null");
        assertNull(b.get("source"), "missing text stays null");
        assertNull(b.get("retry_count"), "missing integer stays null");
        assertNull(b.get("timed_out"), "missing boolean stays null");
        assertNull(b.get("plan_result_errors"), "missing jsonb stays null");
    }

    @Test
    void genericBooleanNumericAndScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("forced", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("forced", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("item_count", "6"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("retry_count", "0"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("status", "Failed"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("integration", "corp directory"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("status", "failed"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "App_IDJ0001002", "name", "0000000001"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("identity_name", "App_IDJ0001002", "name", "nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // plan_result_errors jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("plan_result_errors", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("0000000002", win.get(0).get("name"));
        // filter first, then window: one Failed row, offset past it -> empty
        assertEquals(1, svc.fetch(source(), f("status", "Failed"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("name", "0000000001"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
