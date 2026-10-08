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
 * Contract for the KF Agent Policy read service: reuses the native Policy paging loop + parser over a fake
 * page source (no live IIQ, no DB), returns our 17 DB-named fields, excludes the policyid PK + lineage,
 * serializes {@code descriptions} as JSON while the structured-looking text fields (violation_rule,
 * violation_workflow, signature, certification_actions) stay strings, preserves null, and supports generic
 * exact filtering on any scalar field. Mirrors the two SOD-template validation rows. Also verifies empty→[].
 */
class NativePolicyRestServiceTest {

    private final NativePolicyRestService svc = new NativePolicyRestService();

    private static final String SOD = "7f000101971416688197147685e90115";
    private static final String ESOD = "7f000101971416688197147685ef0116";

    private static String sod() {
        return "{\"sourceId\":\"" + SOD + "\",\"name\":\"SOD Template\",\"type\":\"SOD\","
                + "\"typeKey\":\"policy_type_sod\",\"descriptions\":{},"
                + "\"executor\":\"sailpoint.policy.SODPolicyExecutor\",\"constraintCount\":0,"
                + "\"state\":\"Inactive\",\"certificationActions\":\"Remediated,Mitigated,Delegated\","
                // violation-owner resolution (type=Rule) + template flag
                + "\"violationOwnerType\":\"Rule\",\"violationOwnerRuleId\":\"rule-1\","
                + "\"violationOwnerRuleName\":\"SOD Owner Rule\",\"template\":true,"
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Policy\",\"extractionRunId\":\"run-1\"}";
    }

    private static String entitlementSod() {
        return "{\"sourceId\":\"" + ESOD + "\",\"name\":\"Entitlement SOD Template\",\"type\":\"EntitlementSOD\","
                + "\"typeKey\":\"policy_type_entitlement_sod\",\"descriptions\":{},"
                + "\"executor\":\"sailpoint.policy.EntitlementSODPolicyExecutor\",\"constraintCount\":0,"
                + "\"state\":\"Inactive\",\"certificationActions\":\"Remediated,Mitigated,Delegated\","
                // violation owner resolved by Manager (no rule), not a template
                + "\"violationOwnerType\":\"Manager\",\"template\":false}";
    }

    private static NativePolicyPageSource source() {
        return (start, limit) -> start == 0
                ? "{\"rows\":[" + sod() + "," + entitlementSod() + "]}"
                : "{\"rows\":[]}";
    }

    private static NativePolicyPageSource emptySource() {
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
        assertEquals(SOD, r.get("source_id"));
        assertEquals("SOD Template", r.get("name"));
        assertEquals("SOD", r.get("type"));
        assertEquals("policy_type_sod", r.get("type_key"));
        assertEquals("sailpoint.policy.SODPolicyExecutor", r.get("executor"));
        assertEquals("Inactive", r.get("state"));
        assertEquals(Integer.valueOf(0), r.get("constraint_count"));
        assertEquals(21, r.size(), "exactly the 21 SailPoint-facing fields (17 original + 4 new)");

        assertFalse(r.containsKey("policyid"));
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
    void descriptionsIsJsonTextFieldsAreStringsAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        assertTrue(r.get("descriptions") instanceof JsonNode, "descriptions is jsonb -> JSON object");
        assertTrue(((JsonNode) r.get("descriptions")).isObject());
        // certification_actions is a TEXT column (comma-joined string), NOT an array
        assertEquals("Remediated,Mitigated,Delegated", r.get("certification_actions"));
        // unset text fields stay null (not manufactured)
        assertNull(r.get("violation_rule"));
        assertNull(r.get("violation_workflow"));
        assertNull(r.get("signature"));
        assertNull(r.get("description"));
        assertNull(r.get("violation_owner_id"));
    }

    @Test
    void violationOwnerTypeRuleAndTemplateFieldsPopulatedAndNullPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> sod = rows.get(0);
        assertEquals("Rule", sod.get("violation_owner_type"));
        assertEquals("rule-1", sod.get("violation_owner_rule_id"));
        assertEquals("SOD Owner Rule", sod.get("violation_owner_rule_name"));
        assertEquals(Boolean.TRUE, sod.get("is_template"));

        Map<String, Object> esod = rows.get(1);
        assertEquals("Manager", esod.get("violation_owner_type"));
        assertEquals(Boolean.FALSE, esod.get("is_template"));
        assertNull(esod.get("violation_owner_rule_id"), "no rule when owner type is Manager");
        assertNull(esod.get("violation_owner_rule_name"));
    }

    @Test
    void genericScalarAndNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("name", "SOD Template"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "EntitlementSOD"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type_key", "policy_type_sod"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("state", "Inactive"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("constraint_count", "0"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("certification_actions", "Remediated,Mitigated,Delegated"), null, null).size());
        // new scalar/boolean filters
        assertEquals(1, svc.fetch(source(), f("violation_owner_type", "Rule"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("violation_owner_type", "Manager"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("violation_owner_rule_name", "SOD Owner Rule"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_template", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_template", "false"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "SOD", "state", "Inactive"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "SOD", "state", "Active"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // descriptions jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("descriptions", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(ESOD, win.get(0).get("source_id"));
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
