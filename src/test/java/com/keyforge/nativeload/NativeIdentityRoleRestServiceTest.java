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
 * Contract for the KF Agent Identity-Role read service: reuses the native identity-role paging loop +
 * parser over a fake page source (no live IIQ, no DB), returns our DB-named edge fields, excludes the
 * identityroleid PK + lineage, serializes {@code targets} as JSON while {@code detection_assignment_ids}
 * stays a string, preserves null/empty, and supports generic exact filtering on any scalar/text field.
 * Covers the assignment-provenance fields (assigner/assigned_date/start_date/end_date/source/negative/manual
 * from {@code sailpoint.object.Assignment}) and the enriched target JSON (displayName/elevatedAccess).
 */
class NativeIdentityRoleRestServiceTest {

    private final NativeIdentityRoleRestService svc = new NativeIdentityRoleRestService();

    /** ASSIGNED edge with full Assignment provenance + a RoleTarget carrying displayName/elevatedAccess. */
    private static String assigned() {
        return "{\"identityId\":\"i1\",\"identityName\":\"App_IDJ0001008\",\"roleId\":\"r1\","
                + "\"roleName\":\"Engineering-Base\",\"relationshipType\":\"ASSIGNED\",\"assignmentId\":\"asg-1\","
                + "\"assigner\":\"spadmin\",\"assignedDate\":\"2026-10-02T09:00:00Z\","
                + "\"startDate\":\"2026-10-02T09:00:00Z\",\"endDate\":\"2026-12-31T00:00:00Z\","
                + "\"source\":\"LCM\",\"negative\":false,\"manual\":true,"
                + "\"detectionAssignmentIds\":\"\",\"comments\":\"granted by rule\","
                + "\"futureAssignment\":false,\"promotedSoftPermit\":false,"
                + "\"targets\":[{\"applicationId\":\"app-1\",\"applicationName\":\"Directory\","
                + "\"nativeIdentity\":\"App_IDJ0001008\",\"roleName\":\"Engineering-Base\","
                + "\"displayName\":\"Engineering Base\",\"elevatedAccess\":true}],"
                // lineage emitted by the plugin — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Identity.assignedRoles\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    /** DETECTED edge: no Assignment provenance (assigner/dates/source/negative/manual all null). */
    private static String detected() {
        return "{\"identityId\":\"i2\",\"identityName\":\"Alexander Evans\",\"roleId\":\"r2\","
                + "\"roleName\":\"IT-Admin\",\"relationshipType\":\"DETECTED\","
                + "\"detectionAssignmentIds\":\"a1,a2\",\"detectionDate\":\"2026-09-01T10:00:00Z\","
                + "\"targets\":{\"application\":\"EntraTarget\"}}";
    }

    private static NativeIdentityRolePageSource source() {
        String envelope = "{\"entity\":\"IdentityRole\",\"rows\":[" + assigned() + "," + detected() + "]}";
        String empty = "{\"entity\":\"IdentityRole\",\"rows\":[]}";
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
        assertEquals("i1", r.get("identity_id"));
        assertEquals("App_IDJ0001008", r.get("identity_name"));
        assertEquals("Engineering-Base", r.get("role_name"));
        assertEquals("ASSIGNED", r.get("relationship_type"));
        assertEquals(Boolean.FALSE, r.get("future_assignment"));
        assertEquals(19, r.size(), "exactly the SailPoint-facing Identity-Role fields (12 original + 7 provenance)");

        assertFalse(r.containsKey("identityroleid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_hash")); // edge has no source_hash
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void populatedAssignmentProvenanceFields() {
        Map<String, Object> a = svc.fetch(source(), null, null, null).get(0);
        assertEquals("spadmin", a.get("assigner"));
        assertEquals("2026-10-02T09:00:00Z", a.get("assigned_date"));
        assertEquals("2026-10-02T09:00:00Z", a.get("start_date"));
        assertEquals("2026-12-31T00:00:00Z", a.get("end_date"));
        assertEquals("LCM", a.get("source"));
        assertEquals(Boolean.FALSE, a.get("negative"));
        assertEquals(Boolean.TRUE, a.get("manual"));
    }

    @Test
    void nullAssignmentProvenanceOnDetectedEdge() {
        Map<String, Object> d = svc.fetch(source(), null, null, null).get(1);
        assertNull(d.get("assigner"));
        assertNull(d.get("assigned_date"));
        assertNull(d.get("start_date"));
        assertNull(d.get("end_date"));
        assertNull(d.get("source"));
        assertNull(d.get("negative"));
        assertNull(d.get("manual"));
    }

    @Test
    void targetsIncludeDisplayNameAndElevatedAccess() {
        Map<String, Object> a = svc.fetch(source(), null, null, null).get(0);
        assertTrue(a.get("targets") instanceof JsonNode, "targets is jsonb -> JSON");
        JsonNode targets = (JsonNode) a.get("targets");
        assertTrue(targets.isArray());
        JsonNode t0 = targets.get(0);
        assertEquals("Directory", t0.get("applicationName").asText());
        assertEquals("Engineering Base", t0.get("displayName").asText(), "RoleTarget.getDisplayName()");
        assertTrue(t0.get("elevatedAccess").asBoolean(), "RoleTarget.isElevatedAccess()");
    }

    @Test
    void detectionIdsIsStringAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);
        assertEquals("", a.get("detection_assignment_ids"), "detection_assignment_ids is text -> empty string preserved");
        assertNull(a.get("detection_date"), "unset detection_date stays null");
        assertEquals("asg-1", a.get("assignment_id"), "assigned edge keeps its assignment_id");

        Map<String, Object> d = rows.get(1);
        assertTrue(((JsonNode) d.get("targets")).isObject(), "object targets preserved as JSON");
        assertEquals("a1,a2", d.get("detection_assignment_ids"));
        assertNull(d.get("assignment_id"), "unset assignment_id stays null on the detected edge");
        assertNull(d.get("comments"));
    }

    @Test
    void genericScalarAndTextFilters() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "App_IDJ0001008"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("relationship_type", "ASSIGNED"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("relationship_type", "DETECTED"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("role_name", "IT-Admin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("detection_assignment_ids", "a1,a2"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("future_assignment", "false"), null, null).size());
        // new provenance scalar filters
        assertEquals(1, svc.fetch(source(), f("assigner", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("source", "LCM"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("negative", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("manual", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("assigned_date", "2026-10-02T09:00:00Z"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("manual", "false"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("assigner", "spadmin", "relationship_type", "ASSIGNED"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("assigner", "spadmin", "relationship_type", "DETECTED"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // targets jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("targets", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("i2", win.get(0).get("identity_id"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("relationship_type", "ASSIGNED"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("relationship_type", "ASSIGNED"), 1, 5).size());
    }
}
