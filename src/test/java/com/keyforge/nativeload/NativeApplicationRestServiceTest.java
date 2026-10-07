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
 * Contract for the KF Agent Application read service: reuses the native paging loop + parser over a fake
 * page source (no live IIQ, no DB), returns our DB-named SailPoint Application fields, excludes the
 * applicationid PK + lineage, serializes nested jsonb as JSON (not strings), preserves null/[]/{}, and
 * supports generic exact filtering on any scalar response field — applied over the full population first.
 */
class NativeApplicationRestServiceTest {

    private final NativeApplicationRestService svc = new NativeApplicationRestService();

    /** Rows shaped like the plugin wire (NativeApplicationWire keys). */
    private static String entraAuth() {
        return "{\"sourceId\":\"a1\",\"name\":\"EntraAuth\",\"type\":\"Azure Active Directory\","
                + "\"connector\":\"AzureADConnector\",\"authoritative\":true,\"logical\":false,"
                + "\"ownerName\":\"spadmin\",\"score\":5,"
                + "\"schemas\":[{\"objectType\":\"account\"}],\"attributes\":{\"k\":\"v\"},"
                + "\"secondaryOwners\":[],\"serviceAccountFilter\":{},"
                + "\"provisioningConfig\":\"<ProvisioningConfig/>\","
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Application\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-07T00:00:00Z\"}";
    }

    private static String workday() {
        return "{\"sourceId\":\"a2\",\"name\":\"Workday\",\"type\":\"DelimitedFile\","
                + "\"authoritative\":false,\"logical\":false,\"ownerName\":\"hr.admin\",\"score\":0}";
    }

    private static NativeApplicationPageSource source() {
        String envelope = "{\"entity\":\"Application\",\"rows\":[" + entraAuth() + "," + workday() + "]}";
        String empty = "{\"entity\":\"Application\",\"rows\":[]}";
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
        assertEquals("a1", r.get("source_id"));
        assertEquals("EntraAuth", r.get("name"));
        assertEquals("Azure Active Directory", r.get("type"));
        assertEquals("AzureADConnector", r.get("connector"));
        assertEquals(Boolean.TRUE, r.get("authoritative"));
        assertEquals("spadmin", r.get("owner_name"));
        assertEquals(58, r.size(), "exactly the SailPoint-facing Application fields");

        assertFalse(r.containsKey("applicationid"));
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
    void nestedJsonAndTextConfigAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        assertTrue(r.get("schemas") instanceof JsonNode, "schemas is a JSON array");
        assertTrue(((JsonNode) r.get("schemas")).isArray());
        assertTrue(r.get("attributes") instanceof JsonNode, "attributes is a JSON object");
        assertTrue(((JsonNode) r.get("secondary_owners")).isArray(), "empty array preserved");
        assertTrue(((JsonNode) r.get("service_account_filter")).isObject(), "empty object preserved");
        // provisioning_config is a TEXT column, stays a string (not re-parsed)
        assertEquals("<ProvisioningConfig/>", r.get("provisioning_config"));
        // unset nested field stays null (not manufactured)
        assertNull(r.get("dependencies"));
        assertNull(r.get("remediators"));
        // unset scalar stays null
        assertNull(r.get("cluster"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "EntraAuth"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Azure Active Directory"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("authoritative", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("logical", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("owner_name", "spadmin"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "Azure Active Directory", "authoritative", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "DelimitedFile", "authoritative", "true"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // nested jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("schemas", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("a2", win.get(0).get("source_id"));
    }
}
