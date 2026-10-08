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
 * Contract for the KF Agent Certification-Entity read service: reuses the EXISTING native import over a fake
 * page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 32 DB-named
 * business fields (all scalar), excludes the certificationentityid PK + lineage/soft-delete, keeps
 * entity_delegated boolean / composite_score integer / timestamps ISO, preserves null, and supports generic
 * exact filtering on any scalar field. Mirrors the two supplied Identity-type / Open CertificationEntity rows.
 */
class NativeCertificationEntityRestServiceTest {

    private final NativeCertificationEntityRestService svc = new NativeCertificationEntityRestService();

    private static final String EVANS = "7f000101a08f1fbf81a0a5bc42b327f2";
    private static final String COOK = "7f000101a08f1fbf81a0a5d105ce282c";

    /** Sample 1: Alexander Evans — Identity/Open, entityDelegated false, compositeScore 257, optional fields null. */
    private static String evans() {
        return "{\"sourceId\":\"" + EVANS + "\",\"certificationId\":\"7f000101a08f1fbf81a0a5bc304b27d0\","
                + "\"identity\":\"Alexander Evans\",\"firstName\":\"Alexander\",\"lastName\":\"Evans\","
                + "\"fullName\":\"Alexander Evans\",\"snapshotId\":\"7f0001019eb91d3c819ed686060a3289\","
                + "\"type\":\"Identity\",\"summaryStatus\":\"Open\",\"entityDelegated\":false,\"compositeScore\":257,"
                + "\"targetId\":\"7f00010198421229819849ebc9ef0c71\",\"targetName\":\"Alexander Evans\","
                + "\"targetDisplayName\":\"Alexander Evans\",\"created\":\"2026-09-15T15:42:53.619Z\","
                + "\"modified\":\"2026-10-02T15:23:52.185Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.CertificationEntity\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:48:58.939Z\"}";
    }

    /** Sample 2: Alexander Cook — different certification + snapshot; used for filtering + windowing. */
    private static String cook() {
        return "{\"sourceId\":\"" + COOK + "\",\"certificationId\":\"7f000101a08f1fbf81a0a5d0e07e2819\","
                + "\"identity\":\"Alexander Cook\",\"firstName\":\"Alexander\",\"lastName\":\"Cook\","
                + "\"fullName\":\"Alexander Cook\",\"snapshotId\":\"7f0001019eb91d3c819ed6860674328f\","
                + "\"type\":\"Identity\",\"summaryStatus\":\"Open\",\"entityDelegated\":false,\"compositeScore\":257,"
                + "\"targetId\":\"7f00010198421229819849ebca370c93\",\"targetName\":\"Alexander Cook\","
                + "\"targetDisplayName\":\"Alexander Cook\",\"created\":\"2026-09-15T16:05:34.286Z\","
                + "\"modified\":\"2026-09-15T16:09:17.136Z\"}";
    }

    private static NativeCertificationEntityPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + evans() + "," + cook() + "]}" : "{\"rows\":[]}";
    }

    private static NativeCertificationEntityPageSource emptySource() {
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
        assertEquals(EVANS, r.get("source_id"));
        assertEquals("7f000101a08f1fbf81a0a5bc304b27d0", r.get("certification_id"));
        assertEquals("Alexander Evans", r.get("identity"));
        assertEquals("Alexander", r.get("first_name"));
        assertEquals("Evans", r.get("last_name"));
        assertEquals("Alexander Evans", r.get("full_name"));
        assertEquals("Identity", r.get("type"));
        assertEquals("Open", r.get("summary_status"));
        assertEquals("7f0001019eb91d3c819ed686060a3289", r.get("snapshot_id"));
        assertEquals("Alexander Evans", r.get("target_display_name"));
        assertEquals(32, r.size(), "exactly the 32 SailPoint-facing business fields");

        assertFalse(r.containsKey("certificationentityid"));
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
    void booleanNumericTimestampTypesAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);
        // boolean stays boolean
        assertEquals(Boolean.FALSE, r.get("entity_delegated"));
        // numeric stays numeric
        assertEquals(Integer.valueOf(257), r.get("composite_score"));
        // timestamps serialize as ISO strings
        assertEquals("2026-09-15T15:42:53.619Z", r.get("created_at"));
        assertEquals("2026-10-02T15:23:52.185Z", r.get("modified_at"));
        // optional fields preserved as null (not manufactured)
        assertNull(r.get("application"));
        assertNull(r.get("native_identity"));
        assertNull(r.get("account_group"));
        assertNull(r.get("reference_attribute"));
        assertNull(r.get("schema_object_type"));
        assertNull(r.get("pending_certification"));
        assertNull(r.get("entity_delegation_status"));
        assertNull(r.get("completed"));
        assertNull(r.get("owner_id"));
        assertNull(r.get("owner_name"));
        assertNull(r.get("action_status"));
        assertNull(r.get("action_decision_date"));
        assertNull(r.get("action_remediation_action"));
        assertNull(r.get("action_actor_name"));
        assertNull(r.get("action_actor_display_name"));
        assertNull(r.get("action_comments"));
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", EVANS), null, null).size());
        assertEquals(1, svc.fetch(source(), f("identity", "Alexander Cook"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("certification_id", "7f000101a08f1fbf81a0a5bc304b27d0"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("type", "Identity"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("summary_status", "Open"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("entity_delegated", "false"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("composite_score", "257"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("last_name", "Evans"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("summary_status", "open"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "Identity", "last_name", "Cook"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "Identity", "last_name", "Nobody"), null, null).size());
    }

    @Test
    void unknownFilterFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("certificationentityid", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("Alexander Cook", win.get(0).get("identity"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("type", "Identity"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("last_name", "Evans"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
