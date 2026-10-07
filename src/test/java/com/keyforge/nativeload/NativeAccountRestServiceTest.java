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
 * Contract for the KF Agent Account read service: reuses the native Link paging loop + parser over a fake
 * page source (no live IIQ, no DB), returns our DB-named SailPoint Account fields, excludes the accountid
 * PK + lineage, serializes nested jsonb as JSON (not strings), preserves null/[]/{}, and supports generic
 * exact filtering on any scalar response field — applied over the full population before paging.
 */
class NativeAccountRestServiceTest {

    private final NativeAccountRestService svc = new NativeAccountRestService();

    /** Rows shaped like the plugin wire (NativeLinkWire keys), mirroring the two validation records. */
    private static String evans() {
        return "{\"sourceId\":\"7f00...c72\",\"nativeIdentity\":\"179fcc53\","
                + "\"displayName\":\"Alexander Evans\",\"displayableName\":\"Alexander Evans\","
                + "\"applicationId\":\"7f00...bfc\",\"applicationName\":\"EntraAuth\","
                + "\"identityId\":\"7f00...c71\",\"identityName\":\"Alexander Evans\","
                + "\"disabled\":false,\"locked\":false,\"composite\":false,\"manuallyCorrelated\":false,"
                + "\"hasEntitlements\":true,\"iiqDisabled\":false,\"iiqLocked\":false,"
                + "\"permissions\":[],\"targetPermissions\":[],"
                + "\"attributes\":{\"mail\":\"alexander.evans@x.com\",\"groups\":[\"g1\",\"g2\"]},"
                + "\"entitlementAttributes\":{\"groups\":[\"g1\",\"g2\"]},"
                + "\"attributeMetadata\":[],"
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Link\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T00:00:00Z\"}";
    }

    private static String bailey() {
        return "{\"sourceId\":\"7f00...c83\",\"nativeIdentity\":\"54f9a344\","
                + "\"displayName\":\"Alexander Bailey\",\"displayableName\":\"Alexander Bailey\","
                + "\"applicationId\":\"7f00...bfc\",\"applicationName\":\"EntraAuth\","
                + "\"identityId\":\"7f00...c82\",\"identityName\":\"Alexander Bailey\","
                + "\"disabled\":false,\"locked\":false,\"hasEntitlements\":true,"
                + "\"attributes\":{\"mail\":\"alexander.bailey@x.com\"},\"entitlementAttributes\":{\"groups\":[]}}";
    }

    private static NativeLinkPageSource source() {
        String envelope = "{\"entity\":\"Link\",\"rows\":[" + evans() + "," + bailey() + "]}";
        String empty = "{\"entity\":\"Link\",\"rows\":[]}";
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
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("7f00...c72", r.get("source_id"));
        assertEquals("179fcc53", r.get("native_identity"));
        assertEquals("EntraAuth", r.get("application_name"));
        assertEquals("Alexander Evans", r.get("identity_name"));
        assertEquals(Boolean.TRUE, r.get("has_entitlements"));
        assertEquals(29, r.size(), "exactly the SailPoint-facing Account fields");

        assertFalse(r.containsKey("accountid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash")); // Link has no source_hash
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void nestedJsonAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        assertTrue(r.get("attributes") instanceof JsonNode, "attributes is a JSON object");
        assertTrue(((JsonNode) r.get("attributes")).isObject());
        assertTrue(((JsonNode) r.get("entitlement_attributes")).isObject(), "entitlement_attributes is a JSON object");
        assertTrue(((JsonNode) r.get("permissions")).isArray(), "empty array preserved");
        assertTrue(((JsonNode) r.get("attribute_metadata")).isArray());
        // unset scalar / timestamp stays null (not manufactured)
        assertNull(r.get("instance"));
        assertNull(r.get("last_target_aggregation"));
        // bailey has no composite/iiq flags set -> null
        assertNull(svc.fetch(source(), null, null, null).get(1).get("composite"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("native_identity", "179fcc53"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("application_name", "EntraAuth"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Bailey"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("has_entitlements", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("disabled", "true"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("application_name", "EntraAuth", "identity_name", "Alexander Evans"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("application_name", "EntraAuth", "identity_name", "Nobody"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // nested jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("attributes", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("7f00...c83", win.get(0).get("source_id"));
    }
}
