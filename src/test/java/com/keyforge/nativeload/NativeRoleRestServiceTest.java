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
 * Contract for the KF Agent Role read service: reuses the native Role (Bundle) paging loop + parser over
 * a fake page source (no live IIQ, no DB), returns our DB-named SailPoint Role fields, excludes the
 * roleid PK + lineage, serializes the jsonb fields (descriptions/attributes/selector) as JSON while the
 * text fields (role_type_definition/applications/monitored_applications/scorecard/selector_summary) stay
 * strings, preserves null/empty, and supports generic exact filtering on any scalar/text field.
 */
class NativeRoleRestServiceTest {

    private final NativeRoleRestService svc = new NativeRoleRestService();

    private static String engBase() {
        return "{\"sourceId\":\"r1\",\"name\":\"Engineering-Base\",\"displayName\":\"Engineering Base\","
                + "\"fullName\":\"Engineering-Base\",\"type\":\"business\",\"assignmentId\":\"asg-1\","
                + "\"activityEnabled\":false,\"autoPromotion\":true,\"hasSelector\":true,\"orProfiles\":false,"
                + "\"riskScoreWeight\":100,\"ownerName\":\"spadmin\","
                // jsonb fields -> objects
                + "\"descriptions\":{\"en_US\":\"Base engineering role\"},"
                + "\"attributes\":{\"department\":\"Eng\"},"
                + "\"selector\":{\"type\":\"filter\",\"filter\":\"dept==Eng\"},"
                // text fields -> strings (NOT re-parsed)
                + "\"roleTypeDefinition\":\"<RoleTypeDefinition name='business'/>\","
                + "\"applications\":\"App1,App2\",\"monitoredApplications\":\"\",\"scorecard\":\"score=5\","
                + "\"selectorSummary\":\"population: dept=Eng\","
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Bundle\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    private static String itAdmin() {
        return "{\"sourceId\":\"r2\",\"name\":\"IT-Admin\",\"type\":\"it\",\"activityEnabled\":true,"
                + "\"autoPromotion\":false,\"hasSelector\":false,\"ownerName\":\"spadmin\","
                + "\"descriptions\":{},\"attributes\":{},\"applications\":\"\"}";
    }

    private static NativeRolePageSource source() {
        String envelope = "{\"entity\":\"Role\",\"rows\":[" + engBase() + "," + itAdmin() + "]}";
        String empty = "{\"entity\":\"Role\",\"rows\":[]}";
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
        assertEquals("r1", r.get("source_id"));
        assertEquals("Engineering-Base", r.get("name"));
        assertEquals("business", r.get("type"));
        assertEquals(Boolean.FALSE, r.get("activity_enabled"));
        assertEquals(Boolean.TRUE, r.get("auto_promotion"));
        assertEquals(Integer.valueOf(100), r.get("risk_score_weight"));
        assertEquals("spadmin", r.get("owner_name"));
        assertEquals(33, r.size(), "exactly the SailPoint-facing Role fields");

        assertFalse(r.containsKey("roleid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash")); // Role has no source_hash
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void jsonbFieldsAreJsonAndTextFieldsAreStrings() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        // jsonb -> JSON
        assertTrue(r.get("descriptions") instanceof JsonNode, "descriptions is jsonb -> object");
        assertTrue(((JsonNode) r.get("descriptions")).isObject());
        assertTrue(r.get("attributes") instanceof JsonNode);
        assertTrue(r.get("selector") instanceof JsonNode, "selector is jsonb -> object");
        assertTrue(((JsonNode) r.get("selector")).isObject());
        // text -> String (NOT re-parsed)
        assertEquals("App1,App2", r.get("applications"));
        assertEquals("<RoleTypeDefinition name='business'/>", r.get("role_type_definition"));
        assertEquals("score=5", r.get("scorecard"));
        assertEquals("population: dept=Eng", r.get("selector_summary"));
        assertEquals("", r.get("monitored_applications"), "empty text preserved");
        // unset fields stay null (not manufactured)
        assertNull(r.get("deactivation_date"));
        assertNull(r.get("merge_templates"));
    }

    @Test
    void genericScalarAndTextFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "Engineering-Base"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "business"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("activity_enabled", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("auto_promotion", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("owner_name", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("risk_score_weight", "100"), null, null).size());
        // text field filterable too
        assertEquals(1, svc.fetch(source(), f("applications", "App1,App2"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("name", "Engineering-Base", "type", "business"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("name", "Engineering-Base", "type", "it"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // the 3 jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("descriptions", "{}"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("selector", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("r2", win.get(0).get("source_id"));
    }
}
