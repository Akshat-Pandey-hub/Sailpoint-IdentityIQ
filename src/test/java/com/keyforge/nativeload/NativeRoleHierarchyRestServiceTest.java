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
 * Contract for the KF Agent Role-Hierarchy read service: reuses the native Bundle-relationship import (run())
 * over a fake page source (no live IIQ, no DB) that echoes the generated runId and consistent scan counts,
 * collects only the HIERARCHY edges (ignoring the entitlement edges produced by the same import), returns our
 * 5 DB-named SailPoint-facing fields, excludes the KeyForge PK + canonical-uuid FKs + lineage, preserves null,
 * and supports generic exact filtering on any scalar field. Also verifies the empty case.
 */
class NativeRoleHierarchyRestServiceTest {

    private final NativeRoleHierarchyRestService svc = new NativeRoleHierarchyRestService();

    /** INHERITANCE edge: Engineering-Base inherits Employee-Base. */
    private static String inheritanceEdge() {
        return "{\"sourceRoleId\":\"b1\",\"sourceRoleName\":\"Engineering-Base\","
                + "\"relatedRoleId\":\"b0\",\"relatedRoleName\":\"Employee-Base\","
                + "\"relationshipType\":\"INHERITANCE\","
                // lineage/technical fields — must NOT appear in the response
                + "\"srcNaturalKey\":\"b1|INHERITANCE|b0\",\"sourceSystem\":\"IdentityIQ\","
                + "\"extractionRunId\":\"ignored\",\"extractedAt\":\"2026-10-08T00:00:00Z\"}";
    }

    /** REQUIREMENT edge: Engineering-Base requires Security-Training. */
    private static String requirementEdge() {
        return "{\"sourceRoleId\":\"b1\",\"sourceRoleName\":\"Engineering-Base\","
                + "\"relatedRoleId\":\"b2\",\"relatedRoleName\":\"Security-Training\","
                + "\"relationshipType\":\"REQUIREMENT\"}";
    }

    /** PERMIT edge: Finance-Role permits AP-Role. */
    private static String permitEdge() {
        return "{\"sourceRoleId\":\"b3\",\"sourceRoleName\":\"Finance-Role\","
                + "\"relatedRoleId\":\"b4\",\"relatedRoleName\":\"AP-Role\","
                + "\"relationshipType\":\"PERMIT\"}";
    }

    /** An entitlement edge the SAME import produces — the hierarchy endpoint must IGNORE it. */
    private static String entitlementEdge() {
        return "{\"sourceBundleId\":\"b1\",\"roleName\":\"Engineering-Base\",\"entitlementType\":\"PERMISSION\","
                + "\"applicationId\":\"a2\",\"application\":\"UnixApp\",\"permissionRightsList\":[]}";
    }

    /** Echoes the generated runId + consistent counts so run()'s complete-scan validation passes. */
    private static NativeRoleRelationshipPageSource source() {
        return (start, limit, runId) -> {
            if (start != 0) {
                return "{\"sourceCount\":1,\"returnedBundles\":0,\"extractionRunId\":\"" + runId
                        + "\",\"roleEntitlements\":[],\"roleHierarchy\":[]}";
            }
            return "{\"sourceCount\":1,\"returnedBundles\":1,\"extractionRunId\":\"" + runId
                    + "\",\"roleEntitlements\":[" + entitlementEdge() + "],\"roleHierarchy\":["
                    + inheritanceEdge() + "," + requirementEdge() + "," + permitEdge() + "]}";
        };
    }

    /** No role-hierarchy edges at all (sourceCount/returnedBundles consistent). */
    private static NativeRoleRelationshipPageSource emptySource() {
        return (start, limit, runId) -> "{\"sourceCount\":0,\"returnedBundles\":0,\"extractionRunId\":\"" + runId
                + "\",\"roleEntitlements\":[],\"roleHierarchy\":[]}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsFiveFieldsAndExcludesKeyforgeFieldsAndIgnoresEntitlements() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(3, rows.size(), "only hierarchy edges, never the entitlement edges from the same import");

        Map<String, Object> r = rows.get(0);
        assertEquals("b1", r.get("source_role_id"));
        assertEquals("Engineering-Base", r.get("role_name"));           // DB column role_name = source role's name
        assertEquals("b0", r.get("related_role_source_id"));            // DB column = SailPoint related role id
        assertEquals("Employee-Base", r.get("related_role_name"));
        assertEquals("INHERITANCE", r.get("relationship_type"));
        assertEquals(5, r.size(), "exactly the 5 SailPoint-facing fields");

        assertFalse(r.containsKey("rolehierarchyid"));
        assertFalse(r.containsKey("role_id"));                  // KeyForge canonical uuid of the source role
        assertFalse(r.containsKey("related_role_id"));          // KeyForge canonical uuid of the related role
        assertFalse(r.containsKey("source_role_name"));         // not a real column (was an invented name)
        assertFalse(r.containsKey("src_object_id"));
        assertFalse(r.containsKey("src_natural_key"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
        // no entitlement fields leak in
        assertFalse(r.containsKey("entitlement_type"));
        assertFalse(r.containsKey("application_name"));
    }

    @Test
    void allRelationshipTypesMappedAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals("REQUIREMENT", rows.get(1).get("relationship_type"));
        assertEquals("b2", rows.get(1).get("related_role_source_id"));
        assertEquals("Security-Training", rows.get(1).get("related_role_name"));
        assertEquals("PERMIT", rows.get(2).get("relationship_type"));
        assertEquals("Finance-Role", rows.get(2).get("role_name"));
        // the related-role business fields are verbatim Bundle.getId()/getName()
        assertEquals("b4", rows.get(2).get("related_role_source_id"));
        assertEquals("AP-Role", rows.get(2).get("related_role_name"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(2, svc.fetch(source(), f("source_role_id", "b1"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("relationship_type", "INHERITANCE"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("relationship_type", "REQUIREMENT"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("relationship_type", "PERMIT"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("related_role_name", "AP-Role"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("related_role_source_id", "b4"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("role_name", "Finance-Role"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("relationship_type", "inheritance"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("source_role_id", "b1", "relationship_type", "REQUIREMENT"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("source_role_id", "b1", "relationship_type", "PERMIT"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("rolehierarchyid", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("REQUIREMENT", win.get(0).get("relationship_type"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("source_role_id", "b1"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("source_role_id", "b1"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
