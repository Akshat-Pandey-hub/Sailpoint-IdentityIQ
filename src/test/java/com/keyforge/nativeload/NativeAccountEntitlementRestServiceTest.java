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
 * Contract for the KF Agent Account-Entitlement read service: reuses the native paging loop + parser over
 * a fake page source (no live IIQ, no DB), returns our 9 DB-named scalar fields, excludes the
 * accountentitlementid PK + lineage, preserves null, and supports generic exact filtering on any field.
 */
class NativeAccountEntitlementRestServiceTest {

    private final NativeAccountEntitlementRestService svc = new NativeAccountEntitlementRestService();

    /** Rows shaped like the plugin wire, mirroring the two validation records (instance = null). */
    private static String roberts() {
        return "{\"linkId\":\"7f00..0dbf\",\"identityId\":\"7f00..0cc6\",\"identityName\":\"Alexander Roberts\","
                + "\"applicationId\":\"7f00..0d58\",\"applicationName\":\"EntraTarget\","
                + "\"nativeIdentity\":\"bc6eb61b-9b94-44e6-9e0d-2d58f96f1c2e\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"02cf391b-36ae-4fb2-bcca-3c12448d9560\","
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Link.entitlement\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    private static String evans() {
        return "{\"linkId\":\"7f00..0d6f\",\"identityId\":\"7f00..0cc6\",\"identityName\":\"Alexander Evans\","
                + "\"applicationId\":\"7f00..0d58\",\"applicationName\":\"EntraTarget\","
                + "\"nativeIdentity\":\"179fcc53-99af-43e1-92c6-38c46c6af4ee\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"02cf391b-36ae-4fb2-bcca-3c12448d9560\"}";
    }

    private static NativeAccountEntitlementPageSource source() {
        String envelope = "{\"entity\":\"AccountEntitlement\",\"rows\":[" + roberts() + "," + evans() + "]}";
        String empty = "{\"entity\":\"AccountEntitlement\",\"rows\":[]}";
        return (start, limit) -> start == 0 ? envelope : empty;
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsOurNineFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("7f00..0dbf", r.get("link_id"));
        assertEquals("Alexander Roberts", r.get("identity_name"));
        assertEquals("EntraTarget", r.get("application_name"));
        assertEquals("bc6eb61b-9b94-44e6-9e0d-2d58f96f1c2e", r.get("native_identity"));
        assertEquals("groups", r.get("attribute_name"));
        assertEquals("02cf391b-36ae-4fb2-bcca-3c12448d9560", r.get("attribute_value"));
        assertNull(r.get("instance"), "null instance preserved");
        assertEquals(9, r.size(), "exactly the 9 SailPoint-facing fields");

        assertFalse(r.containsKey("accountentitlementid"));
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
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Evans"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("application_name", "EntraTarget"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("attribute_name", "groups"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("attribute_value", "02cf391b-36ae-4fb2-bcca-3c12448d9560"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("native_identity", "179fcc53-99af-43e1-92c6-38c46c6af4ee"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("identity_name", "Nobody"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("application_name", "EntraTarget", "identity_name", "Alexander Roberts"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("application_name", "EntraTarget", "identity_name", "Nobody"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("accountentitlementid", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("7f00..0d6f", win.get(0).get("link_id"));
    }
}
