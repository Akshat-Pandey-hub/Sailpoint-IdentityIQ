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
 * Contract for the KF Agent Certification-Item read service: reuses the EXISTING native import over a fake
 * page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 55 DB-named
 * business fields, excludes the certificationitemid PK + lineage/soft-delete, keeps the 13 booleans /
 * timestamps native and the two structured fields (application_names/classification_names) as real JSON,
 * preserves null, and supports generic exact filtering on scalar fields only. Mirrors the two supplied
 * Exception items (one decided Complete/Approved, one Open).
 */
class NativeCertificationItemRestServiceTest {

    private final NativeCertificationItemRestService svc = new NativeCertificationItemRestService();

    private static final String DECIDED = "7f000101a08f1fbf81a0a5bc428d27d4";
    private static final String OPEN = "7f000101a08f1fbf81a0a5bc428f27d5";

    /** Sample 1: a decided Exception item — phase Complete, Approved, actor spadmin, application_names array. */
    private static String decided() {
        return "{\"sourceId\":\"" + DECIDED + "\",\"certificationId\":\"7f000101a08f1fbf81a0a5bc304b27d0\","
                + "\"entityId\":\"7f000101a08f1fbf81a0a5bc42b327f2\",\"identity\":\"Alexander Evans\","
                + "\"type\":\"Exception\",\"exceptionApplication\":\"EntraTarget\","
                + "\"exceptionAttributeName\":\"groups\","
                + "\"exceptionAttributeValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\","
                + "\"phase\":\"Complete\",\"summaryStatus\":\"Complete\","
                + "\"completed\":\"2026-10-02T15:23:52.093Z\","
                + "\"iiqElevatedAccess\":true,\"reviewed\":false,\"delegated\":true,\"actedUpon\":true,"
                + "\"historical\":false,\"expired\":false,"
                + "\"shortDescription\":\"Entitlements on EntraTarget\","
                + "\"applicationNames\":[\"EntraTarget\"],\"classificationNames\":[],"
                + "\"actionStatus\":\"Approved\",\"actionDecisionDate\":\"2026-10-02T15:23:51.988Z\","
                + "\"actionDecisionCertificationId\":\"7f000101a08f1fbf81a0a5bc304b27d0\","
                + "\"actionActorName\":\"spadmin\",\"actionActorDisplayName\":\"Molly J\","
                + "\"actionIsApproved\":true,\"actionIsRemediation\":false,\"actionIsMitigation\":false,"
                + "\"actionIsDelegation\":false,\"actionIsRevokeAccount\":false,\"actionIsAutoDecision\":false,"
                + "\"actionIsBulkCertified\":false,"
                + "\"created\":\"2026-09-15T15:42:53.582Z\",\"modified\":\"2026-10-02T15:23:52.185Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.CertificationItem\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:02.958Z\"}";
    }

    /** Sample 2: an undecided Open item — no action fields, empty classification_names. */
    private static String open() {
        return "{\"sourceId\":\"" + OPEN + "\",\"certificationId\":\"7f000101a08f1fbf81a0a5bc304b27d0\","
                + "\"entityId\":\"7f000101a08f1fbf81a0a5bc42b327f2\",\"identity\":\"Alexander Evans\","
                + "\"type\":\"Exception\",\"exceptionApplication\":\"EntraTarget\","
                + "\"exceptionAttributeName\":\"groups\","
                + "\"exceptionAttributeValue\":\"02cf391b-36ae-4fb2-bcca-3c12448d9560\","
                + "\"summaryStatus\":\"Open\",\"iiqElevatedAccess\":false,\"reviewed\":false,\"delegated\":false,"
                + "\"actedUpon\":true,\"historical\":false,"
                + "\"shortDescription\":\"Entitlements on EntraTarget\","
                + "\"applicationNames\":[\"EntraTarget\"],\"classificationNames\":[],"
                + "\"created\":\"2026-09-15T15:42:53.583Z\",\"modified\":\"2026-10-02T15:23:52.185Z\"}";
    }

    private static NativeCertificationItemPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + decided() + "," + open() + "]}" : "{\"rows\":[]}";
    }

    private static NativeCertificationItemPageSource emptySource() {
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
        assertEquals(DECIDED, r.get("source_id"));
        assertEquals("7f000101a08f1fbf81a0a5bc42b327f2", r.get("entity_id"));
        assertEquals("Alexander Evans", r.get("identity"));
        assertEquals("Exception", r.get("type"));
        assertEquals("EntraTarget", r.get("exception_application"));
        assertEquals("groups", r.get("exception_attribute_name"));
        assertEquals("Complete", r.get("phase"));
        assertEquals("Approved", r.get("action_status"));
        assertEquals("Molly J", r.get("action_actor_display_name"));
        assertEquals(55, r.size(), "exactly the 55 SailPoint-facing business fields");

        assertFalse(r.containsKey("certificationitemid"));
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
    void booleanTimestampTypesStructuredJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> d = rows.get(0);

        // booleans stay booleans
        assertEquals(Boolean.TRUE, d.get("iiq_elevated_access"));
        assertEquals(Boolean.FALSE, d.get("reviewed"));
        assertEquals(Boolean.TRUE, d.get("delegated"));
        assertEquals(Boolean.TRUE, d.get("acted_upon"));
        assertEquals(Boolean.TRUE, d.get("action_is_approved"));
        assertEquals(Boolean.FALSE, d.get("action_is_remediation"));
        // timestamps serialize as ISO strings
        assertEquals("2026-10-02T15:23:52.093Z", d.get("completed"));
        assertEquals("2026-10-02T15:23:51.988Z", d.get("action_decision_date"));
        assertEquals("2026-09-15T15:42:53.582Z", d.get("created_at"));

        // structured fields stay real JSON (never stringified)
        Object appNames = d.get("application_names");
        assertTrue(appNames instanceof JsonNode && ((JsonNode) appNames).isArray());
        assertEquals("EntraTarget", ((JsonNode) appNames).get(0).asText());
        Object classNames = d.get("classification_names");
        assertTrue(classNames instanceof JsonNode && ((JsonNode) classNames).isArray());
        assertEquals(0, ((JsonNode) classNames).size());

        // undecided item: action fields null, optional fields null
        Map<String, Object> o = rows.get(1);
        assertEquals("Open", o.get("summary_status"));
        assertNull(o.get("action_status"));
        assertNull(o.get("action_decision_date"));
        assertNull(o.get("action_actor_name"));
        assertNull(o.get("action_is_approved"));
        assertNull(o.get("completed"));
        assertNull(o.get("phase"));
        assertNull(o.get("bundle"));
        assertNull(o.get("target_id"));
        assertNull(o.get("owner_name"));
        assertNull(o.get("policy_violation_id"));
    }

    @Test
    void genericScalarBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", DECIDED), null, null).size());
        assertEquals(2, svc.fetch(source(), f("identity", "Alexander Evans"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("type", "Exception"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("summary_status", "Open"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("summary_status", "Complete"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("action_status", "Approved"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("iiq_elevated_access", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("iiq_elevated_access", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("action_is_approved", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("exception_attribute_value", "02cf391b-36ae-4fb2-bcca-3c12448d9560"), null, null).size());
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "Exception", "summary_status", "Complete"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "Exception", "summary_status", "Revoked"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("certificationitemid", "x"), null, null));
        // the two jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("application_names", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("classification_names", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(OPEN, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("type", "Exception"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("summary_status", "Complete"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
