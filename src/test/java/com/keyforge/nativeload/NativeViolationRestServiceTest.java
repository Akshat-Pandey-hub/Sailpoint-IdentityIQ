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
 * Contract for the KF Agent Policy-Violation read service: reuses the EXISTING native import over a fake page
 * source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 19 DB-named business
 * fields (verified field-by-field against the repository upsert bindings), excludes the violationid PK +
 * record_hash + lineage, keeps active boolean / the two ISO timestamps native, the SoD left/right-bundle +
 * remediation markings as text, and the three structured fields (relevant_apps/violating_entitlements/
 * arguments) as real JSON, preserves null, and supports generic exact filtering on scalar fields only. The
 * preview instance has 0 live violations (→ []); this test drives synthetic rows to lock the contract.
 */
class NativeViolationRestServiceTest {

    private final NativeViolationRestService svc = new NativeViolationRestService();

    private static final String V1 = "7f000101a08f1fbf81a0a5bc304b27d0";
    private static final String V2 = "7f000101a08f1fbf81a0a5d0e07e2819";

    /** An active SoD violation with left/right bundles + structured relevant_apps/violating_entitlements. */
    private static String sodViolation() {
        return "{\"sourceId\":\"" + V1 + "\",\"name\":\"AP-vs-AR SoD\",\"identityId\":\"id-1\","
                + "\"identityName\":\"Alexander Evans\",\"policyId\":\"pol-1\",\"policyName\":\"Finance SOD\","
                + "\"constraintId\":\"con-1\",\"constraintName\":\"AP vs AR\",\"status\":\"Open\",\"active\":true,"
                + "\"leftBundles\":\"Accounts Payable\",\"rightBundles\":\"Accounts Receivable\","
                + "\"entitlementsMarkedForRemediation\":\"\",\"bundlesMarkedForRemediation\":\"Accounts Payable\","
                + "\"relevantApps\":[{\"id\":\"app-1\",\"name\":\"Finance\"}],"
                + "\"violatingEntitlements\":[{\"application\":\"Finance\",\"attribute\":\"role\",\"value\":\"AP\"}],"
                + "\"arguments\":{\"severity\":\"high\"},"
                + "\"created\":\"2026-10-01T00:00:00Z\",\"modified\":\"2026-10-02T00:00:00Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.PolicyViolation\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** A mitigated violation with empty/null structured fields — proves null preservation. */
    private static String mitigated() {
        return "{\"sourceId\":\"" + V2 + "\",\"name\":\"Account-Template GENERIC\",\"identityId\":\"id-2\","
                + "\"identityName\":\"Alexander Cook\",\"policyId\":\"pol-2\",\"policyName\":\"Account Template\","
                + "\"constraintId\":\"con-2\",\"constraintName\":\"Multiple Application Accounts\","
                + "\"status\":\"Mitigated\",\"active\":false,"
                + "\"created\":\"2026-09-15T00:00:00Z\",\"modified\":\"2026-09-16T00:00:00Z\"}";
    }

    private static NativeViolationPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + sodViolation() + "," + mitigated() + "]}" : "{\"rows\":[]}";
    }

    /** The real live state of the preview instance: 0 policy violations. */
    private static NativeViolationPageSource emptySource() {
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
    void returnsNineteenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(V1, r.get("source_id"));
        assertEquals("AP-vs-AR SoD", r.get("name"));
        assertEquals("id-1", r.get("identity_id"));
        assertEquals("Alexander Evans", r.get("identity_name"));
        assertEquals("Finance SOD", r.get("policy_name"));
        assertEquals("AP vs AR", r.get("constraint_name"));
        assertEquals("Open", r.get("status"));
        assertEquals(19, r.size(), "exactly the 19 SailPoint-facing business fields");

        assertFalse(r.containsKey("violationid"));      // KeyForge canonical UUID PK
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void booleanTimestampTextBundlesStructuredJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> v = rows.get(0);

        // boolean stays boolean
        assertEquals(Boolean.TRUE, v.get("active"));
        // timestamps serialize as ISO strings
        assertEquals("2026-10-01T00:00:00Z", v.get("created_at"));
        assertEquals("2026-10-02T00:00:00Z", v.get("modified_at"));
        // SoD left/right bundles + remediation markings are TEXT (strings), not JSON
        assertEquals("Accounts Payable", v.get("left_bundles"));
        assertEquals("Accounts Receivable", v.get("right_bundles"));
        assertEquals("", v.get("entitlements_marked_for_remediation"), "empty string preserved, not null");
        assertEquals("Accounts Payable", v.get("bundles_marked_for_remediation"));

        // the three jsonb fields are real JSON (not stringified)
        assertTrue(v.get("relevant_apps") instanceof JsonNode && ((JsonNode) v.get("relevant_apps")).isArray());
        assertEquals("Finance", ((JsonNode) v.get("relevant_apps")).get(0).get("name").asText());
        assertTrue(v.get("violating_entitlements") instanceof JsonNode && ((JsonNode) v.get("violating_entitlements")).isArray());
        assertEquals("AP", ((JsonNode) v.get("violating_entitlements")).get(0).get("value").asText());
        assertTrue(v.get("arguments") instanceof JsonNode && ((JsonNode) v.get("arguments")).isObject());
        assertEquals("high", ((JsonNode) v.get("arguments")).get("severity").asText());

        // mitigated: active false, null structured + null text-bundle fields preserved
        Map<String, Object> m = rows.get(1);
        assertEquals(Boolean.FALSE, m.get("active"));
        assertEquals("Mitigated", m.get("status"));
        assertNull(m.get("left_bundles"));
        assertNull(m.get("right_bundles"));
        assertNull(m.get("relevant_apps"), "unset jsonb stays null");
        assertNull(m.get("violating_entitlements"));
        assertNull(m.get("arguments"));
    }

    @Test
    void genericScalarBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", V1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Cook"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("policy_name", "Finance SOD"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("constraint_name", "AP vs AR"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("status", "Open"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("status", "Mitigated"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("active", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("active", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("left_bundles", "Accounts Payable"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("status", "open"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("policy_name", "Finance SOD", "active", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("policy_name", "Finance SOD", "active", "false"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("violationid", "x"), null, null));
        // the three jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("relevant_apps", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("violating_entitlements", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("arguments", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(V2, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("active", "true"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("active", "true"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        // the real current state of kf_violation in the preview instance
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
