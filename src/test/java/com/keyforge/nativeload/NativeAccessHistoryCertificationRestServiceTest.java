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
 * Contract for the KF Agent Access-History certification read service: reuses the EXISTING native client +
 * envelope over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL),
 * returns our 11 DB-named business fields, excludes the hist_cert_id PK + lineage, keeps finished/signed/
 * created_at/modified_at ISO, re-hydrates cert_json (text column holding the raw certification payload) to
 * structured JSON, preserves null, and supports generic exact filtering on scalar fields only. The live table
 * is currently empty (→ []); this test drives synthetic rows to lock the contract so real data flows later.
 */
class NativeAccessHistoryCertificationRestServiceTest {

    private final NativeAccessHistoryCertificationRestService svc =
            new NativeAccessHistoryCertificationRestService();

    private static final String C1 = "7f000101a08f1fbf81a0a5bc304b27d0";
    private static final String C2 = "7f000101a08f1fbf81a0a5d0e07e2819";

    /** cert_json arrives as a serialized JSON STRING on the wire (the DB reads it via text()). */
    private static String certJsonString(String certName) {
        String detail = ("{'certName':'" + certName + "','phase':'Signed','decisions':3}").replace('\'', '"');
        return detail.replace("\"", "\\\"");
    }

    /** Record 1: a signed targeted cert — cert_json string, finished/signed timestamps. */
    private static String cert1() {
        return "{\"sourceId\":\"" + C1 + "\",\"certId\":\"cert-1\",\"certType\":\"Focused\","
                + "\"certName\":\"KF Test Targeted Cert\",\"certDisplayName\":\"Access Review for Alexander Evans\","
                + "\"finished\":\"2026-10-02T15:23:52.185Z\",\"signed\":\"2026-10-02T15:24:00.000Z\","
                + "\"certJson\":\"" + certJsonString("KF Test Targeted Cert") + "\","
                + "\"created\":\"2026-10-02T15:20:00.000Z\",\"modified\":\"2026-10-02T15:24:00.000Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.accesshistory.HistoricalCertification\","
                + "\"extractionRunId\":\"run-1\",\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** Record 2: a manager cert without a signed/cert_json payload (proves null preservation). */
    private static String cert2() {
        return "{\"sourceId\":\"" + C2 + "\",\"certId\":\"cert-2\",\"certType\":\"Manager\","
                + "\"certName\":\"KF Mgr Cert\",\"certDisplayName\":\"Manager Review\","
                + "\"finished\":\"2026-09-15T16:09:17.136Z\","
                + "\"created\":\"2026-09-15T16:00:00.000Z\",\"modified\":\"2026-09-15T16:09:17.136Z\"}";
    }

    private static NativeAccessHistoryCertificationRestService.PageSource source() {
        return (start, limit) -> start == 0
                ? "{\"sourceCount\":2,\"rows\":[" + cert1() + "," + cert2() + "]}"
                : "{\"sourceCount\":2,\"rows\":[]}";
    }

    /** The real live state today: no certification history → empty rows. */
    private static NativeAccessHistoryCertificationRestService.PageSource emptySource() {
        return (start, limit) -> "{\"sourceCount\":0,\"rows\":[]}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsElevenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(C1, r.get("source_id"));
        assertEquals("cert-1", r.get("cert_id"));
        assertEquals("Focused", r.get("cert_type"));
        assertEquals("KF Test Targeted Cert", r.get("cert_name"));
        assertEquals("Access Review for Alexander Evans", r.get("cert_display_name"));
        assertEquals(11, r.size(), "exactly the 11 SailPoint-facing business fields");

        assertFalse(r.containsKey("hist_cert_id"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void timestampTypesStructuredCertJsonAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> r = rows.get(0);

        // timestamps serialize as ISO strings
        assertEquals("2026-10-02T15:23:52.185Z", r.get("finished"));
        assertEquals("2026-10-02T15:24:00Z", r.get("signed"));       // Instant.toString() drops trailing .000
        assertEquals("2026-10-02T15:20:00Z", r.get("created_at"));

        // cert_json re-hydrated to STRUCTURED JSON (not an escaped string)
        Object cj = r.get("cert_json");
        assertTrue(cj instanceof JsonNode && ((JsonNode) cj).isObject(),
                "cert_json must be structured JSON, not a stringified blob");
        assertEquals("Signed", ((JsonNode) cj).get("phase").asText());
        assertEquals("KF Test Targeted Cert", ((JsonNode) cj).get("certName").asText());

        // null preservation on the second record
        Map<String, Object> r2 = rows.get(1);
        assertNull(r2.get("signed"), "unset signed stays null");
        assertNull(r2.get("cert_json"), "unset cert_json stays null");
        assertNull(r2.get("name"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", C1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("cert_id", "cert-2"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("cert_type", "Focused"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("cert_type", "Manager"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("cert_name", "KF Mgr Cert"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("cert_type", "focused"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("cert_type", "Focused", "cert_id", "cert-1"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("cert_type", "Focused", "cert_id", "cert-2"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("hist_cert_id", "x"), null, null));
        // cert_json is structured JSON -> not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("cert_json", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(C2, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("cert_type", "Focused"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("cert_type", "Focused"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        // the real current state of kf_access_hist_certification
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
