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
 * Contract for the KF Agent Provisioning-Item read service: reuses the SHARED ProvisioningTransaction
 * native import over a fake page source (no live IIQ, no DB), collecting only the derived item rows;
 * returns our 17 DB-named fields, excludes the provisioningitemid PK + lineage, serializes value_json as
 * JSON while value/permission_* stay strings, keeps assignment boolean + item_index integer, preserves
 * null, and supports generic exact filtering on any scalar field. Mirrors the LDAP dn validation row.
 */
class NativeProvisioningItemRestServiceTest {

    private final NativeProvisioningItemRestService svc = new NativeProvisioningItemRestService();

    private static final String TXN = "7f0001019fbf1a17819fc954917d11fb";

    private static String attributeItem() {
        return "{\"txnSourceId\":\"" + TXN + "\",\"identityName\":\"App_IDJ0001011\",\"itemType\":\"ATTRIBUTE\","
                + "\"operation\":\"Set\",\"applicationName\":\"corp directory-LDAP-Target\","
                + "\"nativeIdentity\":\"App_IDJ0001011\",\"accountOperation\":\"Create\",\"name\":\"dn\","
                + "\"value\":\"uid=App_IDJ0001011,ou=rocktar,dc=moli,dc=org\","
                + "\"valueJson\":\"uid=App_IDJ0001011,ou=rocktar,dc=moli,dc=org\",\"assignment\":false,\"itemIndex\":1,"
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.ProvisioningPlan$AttributeRequest\",\"extractionRunId\":\"run-1\"}";
    }

    private static String permissionItem() {
        return "{\"txnSourceId\":\"" + TXN + "\",\"identityName\":\"App_IDJ0001011\",\"itemType\":\"PERMISSION\","
                + "\"operation\":\"Add\",\"applicationName\":\"UnixApp\",\"permissionTarget\":\"/etc/passwd\","
                + "\"permissionRights\":\"read\",\"assignment\":true,\"itemIndex\":2}";
    }

    /** One transaction carrying two derived items (the shared import pulls items from the txn's items[]). */
    private static NativeProvisioningTxnPageSource source() {
        String txn = "{\"sourceId\":\"" + TXN + "\",\"items\":[" + attributeItem() + "," + permissionItem() + "]}";
        return (start, limit) -> start == 0 ? "{\"rows\":[" + txn + "]}" : "{\"rows\":[]}";
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
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(TXN, r.get("txn_source_id"));
        assertEquals("App_IDJ0001011", r.get("identity_name"));
        assertEquals("ATTRIBUTE", r.get("item_type"));
        assertEquals("Set", r.get("operation"));
        assertEquals("corp directory-LDAP-Target", r.get("application_name"));
        assertEquals("Create", r.get("account_operation"));
        assertEquals("dn", r.get("name"));
        assertEquals(Boolean.FALSE, r.get("assignment"));
        assertEquals(Integer.valueOf(1), r.get("item_index"));
        assertEquals(17, r.size(), "exactly the 17 SailPoint-facing fields");

        assertFalse(r.containsKey("provisioningitemid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash"));
        assertFalse(r.containsKey("src_object_id"));
        assertFalse(r.containsKey("src_natural_key"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void valueJsonIsJsonValueIsTextAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertTrue(a.get("value_json") instanceof JsonNode, "value_json is jsonb -> JSON");
        // value is a plain text column -> String
        assertEquals("uid=App_IDJ0001011,ou=rocktar,dc=moli,dc=org", a.get("value"));
        assertNull(a.get("permission_target"), "unset text field stays null");
        assertNull(a.get("instance"));

        Map<String, Object> b = rows.get(1);
        assertEquals("/etc/passwd", b.get("permission_target"));
        assertEquals("read", b.get("permission_rights"));
        assertEquals(Boolean.TRUE, b.get("assignment"));
        assertNull(b.get("value_json"), "missing value_json stays null");
        assertNull(b.get("value"));
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("item_type", "ATTRIBUTE"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("item_type", "PERMISSION"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("identity_name", "App_IDJ0001011"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("assignment", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("assignment", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("item_index", "2"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "App_IDJ0001011", "operation", "Set"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("identity_name", "App_IDJ0001011", "operation", "Nope"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // value_json jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("value_json", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("PERMISSION", win.get(0).get("item_type"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
