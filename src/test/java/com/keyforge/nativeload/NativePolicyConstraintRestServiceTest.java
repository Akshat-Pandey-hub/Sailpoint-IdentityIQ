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
 * Contract for the KF Agent Policy-Constraint read service: reuses the EXISTING native import over a fake
 * page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 18 DB-named
 * business fields, excludes the policyconstraintid PK + lineage/soft-delete, keeps the four structured fields
 * (left_bundles/right_bundles/selectors/arguments) as real JSON while scalars stay strings/ints/ISO, preserves
 * null, and supports generic exact filtering on scalar fields only. Mirrors the GENERIC "Multiple Application
 * Accounts" row plus an SOD row with populated structured fields.
 */
class NativePolicyConstraintRestServiceTest {

    private final NativePolicyConstraintRestService svc = new NativePolicyConstraintRestService();

    private static final String GENERIC = "7f000101971416688197147685f8011a";
    private static final String SOD = "aaaa0101971416688197147685f80222";

    /** Sample 1: GENERIC "Multiple Application Accounts" — empty structured arrays/object, null optional fields. */
    private static String generic() {
        return "{\"sourceId\":\"" + GENERIC + "\",\"policyId\":\"7f000101971416688197147685f80119\","
                + "\"policyName\":\"Account Template\",\"name\":\"Multiple Application Accounts\","
                + "\"constraintType\":\"GENERIC\",\"weight\":0,\"violationOwnerName\":\"None\","
                + "\"leftBundles\":[],\"rightBundles\":[],\"selectors\":[],\"arguments\":{},"
                + "\"created\":\"2025-05-28T01:16:41.336Z\",\"modified\":\"2025-05-28T01:17:12.636Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.BaseConstraint\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:51:51.643Z\"}";
    }

    /** Sample 2: an SOD constraint with populated left/right bundles, a selector, and arguments. */
    private static String sod() {
        return "{\"sourceId\":\"" + SOD + "\",\"policyId\":\"7f000101971416688197147685f80120\","
                + "\"policyName\":\"Finance SOD\",\"name\":\"AP vs AR\",\"description\":\"Separation of duties\","
                + "\"constraintType\":\"SOD\",\"weight\":500,\"compensatingControl\":\"Manager review\","
                + "\"violationOwnerId\":\"7f00owner01\",\"violationOwnerName\":\"Jane Boss\","
                + "\"violationOwnerType\":\"Identity\","
                + "\"leftBundles\":[{\"id\":\"r-ap\",\"name\":\"Accounts Payable\"}],"
                + "\"rightBundles\":[{\"id\":\"r-ar\",\"name\":\"Accounts Receivable\"}],"
                + "\"selectors\":[{\"type\":\"attribute\"}],\"selectorCount\":1,"
                + "\"arguments\":{\"caseSensitive\":true},"
                + "\"created\":\"2025-06-01T00:00:00Z\",\"modified\":\"2025-06-02T00:00:00Z\"}";
    }

    private static NativePolicyConstraintPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + generic() + "," + sod() + "]}" : "{\"rows\":[]}";
    }

    private static NativePolicyConstraintPageSource emptySource() {
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
    void returnsEighteenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(GENERIC, r.get("source_id"));
        assertEquals("7f000101971416688197147685f80119", r.get("policy_id"));
        assertEquals("Account Template", r.get("policy_name"));
        assertEquals("Multiple Application Accounts", r.get("name"));
        assertEquals("GENERIC", r.get("constraint_type"));
        assertEquals("None", r.get("violation_owner_name"));
        assertEquals(18, r.size(), "exactly the 18 SailPoint-facing business fields");

        assertFalse(r.containsKey("policyconstraintid"));
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
    void numericTimestampTypesStructuredJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> g = rows.get(0);

        // integer stays integer
        assertEquals(Integer.valueOf(0), g.get("weight"));
        // timestamps serialize as ISO strings
        assertEquals("2025-05-28T01:16:41.336Z", g.get("created_at"));
        assertEquals("2025-05-28T01:17:12.636Z", g.get("modified_at"));
        // structured fields stay real JSON (empty arrays/object preserved, not stringified)
        assertTrue(g.get("left_bundles") instanceof JsonNode && ((JsonNode) g.get("left_bundles")).isArray());
        assertEquals(0, ((JsonNode) g.get("left_bundles")).size());
        assertTrue(g.get("right_bundles") instanceof JsonNode && ((JsonNode) g.get("right_bundles")).isArray());
        assertTrue(g.get("selectors") instanceof JsonNode && ((JsonNode) g.get("selectors")).isArray());
        assertTrue(g.get("arguments") instanceof JsonNode && ((JsonNode) g.get("arguments")).isObject());
        // null optional fields preserved
        assertNull(g.get("description"));
        assertNull(g.get("compensating_control"));
        assertNull(g.get("violation_owner_id"));
        assertNull(g.get("violation_owner_type"));
        assertNull(g.get("selector_count"));

        // the SOD row proves populated structured fields + scalars
        Map<String, Object> s = rows.get(1);
        assertEquals(Integer.valueOf(500), s.get("weight"));
        assertEquals(Integer.valueOf(1), s.get("selector_count"));
        assertEquals("Identity", s.get("violation_owner_type"));
        JsonNode left = (JsonNode) s.get("left_bundles");
        assertEquals("Accounts Payable", left.get(0).get("name").asText());
        assertTrue(((JsonNode) s.get("arguments")).get("caseSensitive").asBoolean());
    }

    @Test
    void genericScalarAndNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", GENERIC), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "AP vs AR"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("constraint_type", "GENERIC"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("constraint_type", "SOD"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("policy_name", "Account Template"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("weight", "500"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("selector_count", "1"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("violation_owner_name", "None"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("constraint_type", "SOD", "violation_owner_type", "Identity"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("constraint_type", "SOD", "violation_owner_type", "Manager"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("policyconstraintid", "x"), null, null));
        // the four jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("left_bundles", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("right_bundles", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("selectors", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("arguments", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("AP vs AR", win.get(0).get("name"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("constraint_type", "GENERIC"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("constraint_type", "GENERIC"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
