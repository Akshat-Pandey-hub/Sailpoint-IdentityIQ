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
 * Contract for the KF Agent WorkItem-Archive read service: reuses the EXISTING native (append-only CEC)
 * import over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns
 * our 29 DB-named business fields (verified field-by-field against the repository append bindings), excludes
 * the archiveid PK + record_hash + the CEC lineage envelope, keeps is_signed boolean / the four ISO
 * timestamps / the five structured fields (signoffs/comments/owner_history/system_attributes/attributes) as
 * real JSON, preserves null, and supports generic exact filtering on scalar fields only.
 */
class NativeWorkItemArchiveRestServiceTest {

    private final NativeWorkItemArchiveRestService svc = new NativeWorkItemArchiveRestService();

    private static final String A1 = "7f000101a08f1fbf81a0a5bc42b327f2";
    private static final String A2 = "7f0001019fbf1a17819fcd8d03fd1c25";

    /** A signed certification work item archive with structured signoffs/owner_history/attributes. */
    private static String signedCert() {
        return "{\"sourceId\":\"" + A1 + "\",\"workItemId\":\"wi-1\",\"name\":\"0000000039\","
                + "\"type\":\"Certification\",\"state\":\"Finished\",\"level\":\"Normal\","
                + "\"requester\":\"spadmin\",\"assignee\":\"Jane Boss\",\"ownerName\":\"Jane Boss\","
                + "\"completer\":\"Jane Boss\",\"completionComments\":\"approved all\",\"signed\":true,"
                + "\"certificationId\":\"cert-1\",\"certificationEntityId\":\"ent-1\","
                + "\"certificationItemId\":\"item-1\",\"entityType\":\"Identity\","
                + "\"signOffs\":[{\"signer\":\"Jane Boss\"}],\"comments\":[],\"ownerHistory\":[],"
                + "\"systemAttributes\":{\"workflowCaseId\":\"wc-1\"},\"attributes\":{\"foo\":\"bar\"},"
                + "\"created\":\"2026-08-01T00:00:00Z\",\"modified\":\"2026-08-02T00:00:00Z\","
                + "\"archived\":\"2026-08-03T00:00:00Z\","
                // CEC lineage — must NOT appear in the response (echoes of business fields)
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.WorkItemArchive\",\"srcNaturalKey\":\"wi-1\","
                + "\"srcEventTs\":\"2026-08-03T00:00:00Z\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** A RapidSetup ManualAction archive — not signed, empty structured payloads, many null optional fields. */
    private static String manualAction() {
        return "{\"sourceId\":\"" + A2 + "\",\"workItemId\":\"wi-2\",\"name\":\"0000000040\","
                + "\"type\":\"ManualAction\",\"state\":\"Finished\",\"level\":\"Normal\","
                + "\"requester\":\"spadmin\",\"ownerName\":\"spadmin\",\"signed\":false,"
                + "\"targetClass\":\"sailpoint.object.Identity\",\"targetId\":\"id-9\","
                + "\"targetName\":\"App_IDJ0001005\",\"identityRequestId\":\"ir-1\","
                + "\"signOffs\":[],\"comments\":[],\"ownerHistory\":[],\"attributes\":{},"
                + "\"created\":\"2026-08-04T00:00:00Z\",\"modified\":\"2026-08-04T01:00:00Z\","
                + "\"archived\":\"2026-08-04T02:00:00Z\"}";
    }

    private static NativeWorkItemArchivePageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + signedCert() + "," + manualAction() + "]}" : "{\"rows\":[]}";
    }

    private static NativeWorkItemArchivePageSource emptySource() {
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
    void returnsTwentyNineFieldsAndExcludesKeyforgeAndLineageFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(A1, r.get("source_id"));
        assertEquals("wi-1", r.get("work_item_id"));
        assertEquals("0000000039", r.get("name"));
        assertEquals("Certification", r.get("type"));
        assertEquals("Finished", r.get("state"));
        assertEquals("Jane Boss", r.get("owner_name"));
        assertEquals("cert-1", r.get("certification_id"));
        assertEquals(29, r.size(), "exactly the 29 SailPoint-facing business fields");

        // KeyForge PK + hash
        assertFalse(r.containsKey("archiveid"));
        assertFalse(r.containsKey("record_hash"));
        // CEC lineage envelope (incl. the src_* echoes of business fields)
        assertFalse(r.containsKey("src_system"));
        assertFalse(r.containsKey("src_object_type"));
        assertFalse(r.containsKey("src_object_id"));
        assertFalse(r.containsKey("src_natural_key"));
        assertFalse(r.containsKey("src_created"));
        assertFalse(r.containsKey("src_modified"));
        assertFalse(r.containsKey("src_event_ts"));
        assertFalse(r.containsKey("src_interface"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("raw_ref"));
        assertFalse(r.containsKey("derived_at"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void booleanTimestampStructuredJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> s = rows.get(0);

        // boolean stays boolean
        assertEquals(Boolean.TRUE, s.get("is_signed"));
        // timestamps serialize as ISO strings (archived_ts = authoritative archival timestamp)
        assertEquals("2026-08-01T00:00:00Z", s.get("created_at"));
        assertEquals("2026-08-02T00:00:00Z", s.get("modified_at"));
        assertEquals("2026-08-03T00:00:00Z", s.get("archived_ts"));
        assertNull(s.get("expiration_ts"), "unset expiration stays null");

        // structured fields stay real JSON (not stringified)
        assertTrue(s.get("signoffs") instanceof JsonNode && ((JsonNode) s.get("signoffs")).isArray());
        assertEquals("Jane Boss", ((JsonNode) s.get("signoffs")).get(0).get("signer").asText());
        assertTrue(s.get("comments") instanceof JsonNode && ((JsonNode) s.get("comments")).isArray());
        assertTrue(s.get("owner_history") instanceof JsonNode && ((JsonNode) s.get("owner_history")).isArray());
        assertTrue(s.get("system_attributes") instanceof JsonNode && ((JsonNode) s.get("system_attributes")).isObject());
        assertEquals("wc-1", ((JsonNode) s.get("system_attributes")).get("workflowCaseId").asText());
        assertTrue(s.get("attributes") instanceof JsonNode && ((JsonNode) s.get("attributes")).isObject());

        // manual-action: not signed, null optional fields preserved
        Map<String, Object> ma = rows.get(1);
        assertEquals(Boolean.FALSE, ma.get("is_signed"));
        assertEquals("sailpoint.object.Identity", ma.get("target_class"));
        assertNull(ma.get("assignee"));
        assertNull(ma.get("completer"));
        assertNull(ma.get("completion_comments"));
        assertNull(ma.get("certification_id"));
        assertNull(ma.get("system_attributes"), "unset jsonb stays null");
    }

    @Test
    void genericScalarBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", A1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("work_item_id", "wi-2"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Certification"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "ManualAction"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("state", "Finished"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("requester", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_signed", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_signed", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("certification_id", "cert-1"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "certification"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("state", "Finished", "is_signed", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "Certification", "is_signed", "false"), null, null).size());
    }

    @Test
    void unknownAndStructuredAndLineageFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("archiveid", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("src_event_ts", "x"), null, null));
        // the five jsonb fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("signoffs", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("attributes", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("ManualAction", win.get(0).get("type"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("state", "Finished"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("state", "Finished"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
