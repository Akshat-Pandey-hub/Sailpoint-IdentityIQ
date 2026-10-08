package com.keyforge.nativeload;

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
 * Contract for the KF Agent Role-Entitlement read service: reuses the native Bundle-relationship import
 * (run()) over a fake page source (no live IIQ, no DB) that echoes the generated runId and consistent
 * scan counts, collects only the entitlement edges, returns our 16 DB-named fields (filter_value /
 * permission_rights_list as JSON), excludes the KeyForge PK + canonical-uuid FKs + lineage, preserves
 * null/empty, and supports generic exact filtering on any scalar field. Also verifies the empty case.
 */
class NativeRoleEntitlementRestServiceTest {

    private final NativeRoleEntitlementRestService svc = new NativeRoleEntitlementRestService();

    private static String profileEdge() {
        return "{\"sourceBundleId\":\"b1\",\"roleId\":\"b1\",\"roleName\":\"Engineering-Base\","
                + "\"entitlementType\":\"PROFILE_CONSTRAINT\",\"applicationId\":\"a1\",\"application\":\"EntraTarget\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\","
                + "\"filterOperation\":\"EQ\",\"filterExpression\":\"groups==00edc4ac\","
                + "\"filterValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\",\"profileOrdinal\":0,"
                + "\"permissionRightsList\":[],"
                // lineage/technical fields — must NOT appear in the response
                + "\"srcNaturalKey\":\"b1|0\",\"sourceSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Bundle\",\"extractionRunId\":\"ignored\"}";
    }

    private static String permissionEdge() {
        return "{\"sourceBundleId\":\"b1\",\"roleName\":\"Engineering-Base\",\"entitlementType\":\"PERMISSION\","
                + "\"applicationId\":\"a2\",\"application\":\"UnixApp\",\"permissionTarget\":\"/etc/passwd\","
                + "\"permissionRights\":\"read\",\"permissionRightsList\":[\"read\",\"write\"],"
                + "\"permissionAnnotation\":\"server\",\"profileOrdinal\":1}";
    }

    /** Echoes the generated runId + consistent counts so run()'s complete-scan validation passes. */
    private static NativeRoleRelationshipPageSource source() {
        return (start, limit, runId) -> {
            if (start != 0) {
                return "{\"sourceCount\":1,\"returnedBundles\":0,\"extractionRunId\":\"" + runId
                        + "\",\"roleEntitlements\":[],\"roleHierarchy\":[]}";
            }
            return "{\"sourceCount\":1,\"returnedBundles\":1,\"extractionRunId\":\"" + runId
                    + "\",\"roleEntitlements\":[" + profileEdge() + "," + permissionEdge()
                    + "],\"roleHierarchy\":[]}";
        };
    }

    /** No role-entitlement edges at all (sourceCount/returnedBundles consistent). */
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
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("b1", r.get("source_role_id"));
        assertEquals("Engineering-Base", r.get("role_name"));
        assertEquals("PROFILE_CONSTRAINT", r.get("entitlement_type"));
        assertEquals("EntraTarget", r.get("application_name"));
        assertEquals("groups", r.get("attribute_name"));
        assertEquals("EQ", r.get("filter_operation"));
        assertEquals(Integer.valueOf(0), r.get("profile_ordinal"));
        assertEquals(16, r.size(), "exactly the 16 SailPoint-facing fields");

        assertFalse(r.containsKey("roleentitlementid"));
        assertFalse(r.containsKey("role_id"));          // KeyForge canonical uuid FK
        assertFalse(r.containsKey("entitlement_id"));   // KeyForge canonical uuid FK
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
    }

    @Test
    void structuredFieldsAreJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> profile = rows.get(0);
        assertEquals("00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5", profile.get("filter_value"));
        assertTrue(profile.get("permission_rights_list") instanceof List, "permission_rights_list -> JSON array");
        assertTrue(((List<?>) profile.get("permission_rights_list")).isEmpty(), "empty array preserved");
        assertNull(profile.get("permission_target"), "null permission_target preserved");

        Map<String, Object> perm = rows.get(1);
        assertNull(perm.get("filter_value"), "null filter_value preserved");
        assertEquals(List.of("read", "write"), perm.get("permission_rights_list"));
        assertEquals("/etc/passwd", perm.get("permission_target"));
        assertNull(perm.get("attribute_name"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(2, svc.fetch(source(), f("role_name", "Engineering-Base"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("entitlement_type", "PROFILE_CONSTRAINT"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("entitlement_type", "PERMISSION"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("application_name", "EntraTarget"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("attribute_value", "00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("profile_ordinal", "1"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("role_name", "Engineering-Base", "entitlement_type", "PERMISSION"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("role_name", "Engineering-Base", "entitlement_type", "NONE"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // structured fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("filter_value", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("permission_rights_list", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("PERMISSION", win.get(0).get("entitlement_type"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
