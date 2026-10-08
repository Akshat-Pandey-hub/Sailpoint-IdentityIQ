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
 * Contract for the KF Agent Certification-Archive read service: reuses the EXISTING native (append-only CEC)
 * import over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns
 * our 14 DB-named business fields (verified field-by-field against the repository append bindings), excludes
 * the certificationarchiveid PK + record_hash + the CEC lineage envelope, keeps the four ISO timestamps,
 * re-hydrates child_certification_ids to a JSON array, preserves archive_xml as the raw historical XML text
 * (non-filterable) and preserves null, and supports generic exact filtering on scalar fields only. The live
 * table is currently empty (→ []); this test drives synthetic rows to lock the contract so real data flows.
 */
class NativeCertificationArchiveRestServiceTest {

    private final NativeCertificationArchiveRestService svc = new NativeCertificationArchiveRestService();

    private static final String A1 = "7f000101a08f1fbf81a0a5bc304b27d0";
    private static final String A2 = "7f000101a08f1fbf81a0a5d0e07e2819";

    /** A signed, archived certification with child ids + an XML blob. */
    private static String archive1() {
        return "{\"sourceId\":\"" + A1 + "\",\"name\":\"KF Test Targeted Cert Archive\","
                + "\"certificationId\":\"cert-1\",\"certificationGroupId\":\"cg-1\","
                + "\"creatorName\":\"spadmin\",\"ownerName\":\"Jane Boss\",\"comments\":\"all reviewed\","
                + "\"signed\":\"2026-10-02T15:24:00Z\",\"expiration\":\"2026-12-01T00:00:00Z\","
                + "\"childCertificationIds\":[\"cert-1a\",\"cert-1b\"],"
                + "\"archiveXml\":\"<Certification id=\\\"cert-1\\\"><entity name=\\\"Alexander Evans\\\"/></Certification>\","
                + "\"created\":\"2026-10-02T15:23:52.185Z\",\"modified\":\"2026-10-02T15:24:10Z\","
                // CEC lineage — must NOT appear in the response (echoes of business fields)
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.CertificationArchive\",\"srcNaturalKey\":\"cert-1\","
                + "\"srcEventTs\":\"2026-10-02T15:23:52.185Z\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** A manager-cert archive with no child ids / xml / signed — proves null preservation. */
    private static String archive2() {
        return "{\"sourceId\":\"" + A2 + "\",\"name\":\"KF Mgr Cert Archive\",\"certificationId\":\"cert-2\","
                + "\"creatorName\":\"spadmin\",\"comments\":\"\","
                + "\"created\":\"2026-09-15T16:09:17.136Z\",\"modified\":\"2026-09-15T16:10:00Z\"}";
    }

    private static NativeCertificationArchivePageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + archive1() + "," + archive2() + "]}" : "{\"rows\":[]}";
    }

    /** The real live state today: no certification archives → empty rows. */
    private static NativeCertificationArchivePageSource emptySource() {
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
    void returnsFourteenFieldsAndExcludesKeyforgeAndLineageFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(A1, r.get("source_id"));
        assertEquals("KF Test Targeted Cert Archive", r.get("name"));
        assertEquals("cert-1", r.get("certification_id"));
        assertEquals("cg-1", r.get("certification_group_id"));
        assertEquals("spadmin", r.get("creator_name"));
        assertEquals("Jane Boss", r.get("owner_name"));
        assertEquals("all reviewed", r.get("comments"));
        assertEquals(14, r.size(), "exactly the 14 SailPoint-facing business fields");

        assertFalse(r.containsKey("certificationarchiveid"));   // KeyForge canonical UUID PK
        assertFalse(r.containsKey("record_hash"));
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
    void timestampTypesStructuredChildIdsArchiveXmlAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> a = rows.get(0);

        // timestamps serialize as ISO strings; archived_ts == created_at by design
        assertEquals("2026-10-02T15:24:00Z", a.get("signed"));
        assertEquals("2026-12-01T00:00:00Z", a.get("expiration"));
        assertEquals("2026-10-02T15:23:52.185Z", a.get("created_at"));
        assertEquals("2026-10-02T15:24:10Z", a.get("modified_at"));
        assertEquals("2026-10-02T15:23:52.185Z", a.get("archived_ts"));

        // child_certification_ids -> real JSON array (not stringified)
        Object kids = a.get("child_certification_ids");
        assertTrue(kids instanceof JsonNode && ((JsonNode) kids).isArray());
        assertEquals("cert-1a", ((JsonNode) kids).get(0).asText());
        assertEquals("cert-1b", ((JsonNode) kids).get(1).asText());

        // archive_xml -> raw historical XML preserved verbatim as a string
        assertTrue(a.get("archive_xml") instanceof String);
        assertTrue(((String) a.get("archive_xml")).contains("<Certification id=\"cert-1\">"));

        // null preservation on the sparse archive
        Map<String, Object> b = rows.get(1);
        assertNull(b.get("certification_group_id"));
        assertNull(b.get("owner_name"));
        assertNull(b.get("signed"));
        assertNull(b.get("expiration"));
        assertNull(b.get("child_certification_ids"), "unset jsonb stays null");
        assertNull(b.get("archive_xml"), "unset archive_xml stays null");
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", A1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("certification_id", "cert-2"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "KF Mgr Cert Archive"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("creator_name", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("owner_name", "Jane Boss"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("certification_group_id", "cg-1"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("creator_name", "SPADMIN"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("creator_name", "spadmin", "certification_id", "cert-1"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("creator_name", "spadmin", "certification_id", "nope"), null, null).size());
    }

    @Test
    void unknownStructuredAndLineageFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("certificationarchiveid", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("src_event_ts", "x"), null, null));
        // structured / document fields are not scalar-filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("child_certification_ids", "[]"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("archive_xml", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(A2, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("creator_name", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("creator_name", "spadmin"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        // the real current state of kf_certification_archive
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
