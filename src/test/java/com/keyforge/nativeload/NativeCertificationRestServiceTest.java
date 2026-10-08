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
 * Contract for the KF Agent Certification read service: reuses the EXISTING native Certification import over a
 * fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 43
 * DB-named business fields, excludes the certificationid PK + lineage/soft-delete, keeps the four structured
 * fields (certifiers/sign_off_history/allowed_statuses/tags) as real JSON (never stringified — including the
 * two that the native parser stores as serialized array strings), keeps booleans/integers/timestamps native,
 * preserves null, and supports generic exact filtering on any scalar field. Mirrors the supplied Focused /
 * Active "Targeted Access Review for Alexander Evans" record.
 */
class NativeCertificationRestServiceTest {

    private final NativeCertificationRestService svc = new NativeCertificationRestService();

    private static final String CERT_1 = "7f000101a08f1fbf81a0a5bc304b27d0";
    private static final String CERT_2 = "aaaa0101a08f1fbf81a0a5bc30000002";

    /**
     * Sample 1: Focused/Active cert. certifiers/signOffHistory are wire JSON arrays (parser json()); the
     * allowedStatuses wire value is a serialized JSON-array STRING (parser text() + mapper NativeSerialize),
     * which must still re-hydrate to a real array. manager/signed/finished/activated/owner/tags null.
     */
    private static String cert1() {
        return "{\"sourceId\":\"" + CERT_1 + "\",\"name\":\"Targeted Access Review for Alexander Evans\","
                + "\"certificationName\":\"KF Test Targeted Cert\",\"shortName\":\"Access Review for Alexander Evans\","
                + "\"type\":\"Focused\",\"phase\":\"Active\",\"creator\":\"spadmin\","
                + "\"certificationGroupId\":\"7f000101a08f1fbf81a0a5bc304227cf\","
                + "\"certificationGroupName\":\"KF Test Targeted Cert\","
                + "\"certificationDefinitionId\":\"7f000101a08f1fbf81a0a5bc300127cd\","
                + "\"complete\":false,\"expired\":false,\"continuous\":false,\"electronicallySigned\":false,"
                + "\"expiration\":\"2026-09-15T15:42:53.737Z\",\"created\":\"2026-10-02T15:23:52.282Z\","
                + "\"modified\":\"2026-10-05T15:48:55.202Z\","
                + "\"totalItems\":30,\"completedItems\":1,\"openItems\":29,\"totalEntities\":1,"
                + "\"completedEntities\":0,\"openEntities\":1,\"percentComplete\":0,"
                + "\"certifiers\":[\"Alexander Evans\"],\"signOffHistory\":[],"
                // allowedStatuses arrives as a serialized JSON-array string (text column, mapper-serialized)
                + "\"allowedStatuses\":\"[\\\"Complete\\\",\\\"Open\\\",\\\"Delegated\\\",\\\"WaitingReview\\\",\\\"Returned\\\",\\\"Challenged\\\"]\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.Certification\",\"extractionRunId\":\"run-1\"}";
    }

    /** Sample 2: a completed Manager cert with signed/activated timestamps, owner, tags; used for filters. */
    private static String cert2() {
        return "{\"sourceId\":\"" + CERT_2 + "\",\"name\":\"Manager Access Review\","
                + "\"certificationName\":\"KF Mgr Cert\",\"shortName\":\"Mgr Review\",\"type\":\"Manager\","
                + "\"phase\":\"Staged\",\"creator\":\"spadmin\",\"manager\":\"Jane Boss\","
                + "\"complete\":true,\"expired\":false,\"continuous\":false,\"electronicallySigned\":true,"
                + "\"signed\":\"2026-10-07T00:00:00Z\",\"activated\":\"2026-10-06T00:00:00Z\","
                + "\"expiration\":\"2026-12-01T00:00:00Z\",\"created\":\"2026-10-01T00:00:00Z\","
                + "\"modified\":\"2026-10-02T00:00:00Z\","
                + "\"totalItems\":10,\"completedItems\":10,\"openItems\":0,\"totalEntities\":2,"
                + "\"completedEntities\":2,\"openEntities\":0,\"percentComplete\":100,"
                + "\"certifiers\":[\"Jane Boss\"],\"signOffHistory\":[{\"signer\":\"Jane Boss\"}],"
                + "\"ownerId\":\"7f000101owner0002\",\"ownerName\":\"Jane Boss\","
                + "\"allowedStatuses\":\"[\\\"Complete\\\",\\\"Open\\\"]\",\"tags\":\"[\\\"quarterly\\\"]\"}";
    }

    private static NativeCertificationPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + cert1() + "," + cert2() + "]}" : "{\"rows\":[]}";
    }

    private static NativeCertificationPageSource emptySource() {
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
        assertEquals(CERT_1, r.get("source_id"));
        assertEquals("Targeted Access Review for Alexander Evans", r.get("name"));
        assertEquals("KF Test Targeted Cert", r.get("certification_name"));
        assertEquals("Access Review for Alexander Evans", r.get("short_name"));
        assertEquals("Focused", r.get("type"));
        assertEquals("Active", r.get("phase"));
        assertEquals("spadmin", r.get("creator"));
        assertEquals("7f000101a08f1fbf81a0a5bc304227cf", r.get("certification_group_id"));
        assertEquals(43, r.size(), "exactly the 43 SailPoint-facing business fields");

        // excluded KeyForge/technical columns (PK is not a business field)
        assertFalse(r.containsKey("certificationid"));
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

        // booleans stay booleans
        assertEquals(Boolean.FALSE, r.get("complete"));
        assertEquals(Boolean.FALSE, r.get("expired"));
        assertEquals(Boolean.FALSE, r.get("continuous"));
        assertEquals(Boolean.FALSE, r.get("electronically_signed"));
        // integers stay integers
        assertEquals(Integer.valueOf(30), r.get("total_items"));
        assertEquals(Integer.valueOf(1), r.get("completed_items"));
        assertEquals(Integer.valueOf(29), r.get("open_items"));
        assertEquals(Integer.valueOf(1), r.get("total_entities"));
        assertEquals(Integer.valueOf(0), r.get("completed_entities"));
        assertEquals(Integer.valueOf(1), r.get("open_entities"));
        assertEquals(Integer.valueOf(0), r.get("percent_complete"));
        // timestamps serialize as ISO strings
        assertEquals("2026-09-15T15:42:53.737Z", r.get("expiration"));
        assertEquals("2026-10-02T15:23:52.282Z", r.get("created_at"));
        assertEquals("2026-10-05T15:48:55.202Z", r.get("modified_at"));

        // signed/finished/activated are TIMESTAMPS (not booleans) and are null when unset
        assertNull(r.get("signed"));
        assertNull(r.get("finished"));
        assertNull(r.get("activated"));
        // other nulls preserved exactly
        assertNull(r.get("manager"));
        assertNull(r.get("owner_id"));
        assertNull(r.get("owner_name"));
        assertNull(r.get("automatic_closing_date"));
        assertNull(r.get("tags"));

        // the second record proves signed/activated serialize as ISO timestamps when present
        Map<String, Object> r2 = svc.fetch(source(), null, null, null).get(1);
        assertEquals("2026-10-07T00:00:00Z", r2.get("signed"));
        assertEquals("2026-10-06T00:00:00Z", r2.get("activated"));
        assertEquals(Boolean.TRUE, r2.get("complete"));
        assertEquals("Jane Boss", r2.get("manager"));
        assertEquals("Jane Boss", r2.get("owner_name"));
    }

    @Test
    void structuredFieldsStayRealJsonNotStringified() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);

        Object certifiers = r.get("certifiers");
        assertTrue(certifiers instanceof JsonNode && ((JsonNode) certifiers).isArray(), "certifiers must be JSON");
        assertEquals("Alexander Evans", ((JsonNode) certifiers).get(0).asText());

        Object signOff = r.get("sign_off_history");
        assertTrue(signOff instanceof JsonNode && ((JsonNode) signOff).isArray());
        assertEquals(0, ((JsonNode) signOff).size());

        // allowed_statuses is stored by the parser as a serialized JSON-array STRING -> must still be real JSON
        Object allowed = r.get("allowed_statuses");
        assertTrue(allowed instanceof JsonNode && ((JsonNode) allowed).isArray(),
                "allowed_statuses must be re-hydrated to a JSON array, not an escaped string");
        JsonNode a = (JsonNode) allowed;
        assertEquals(6, a.size());
        assertEquals("Complete", a.get(0).asText());
        assertEquals("Challenged", a.get(5).asText());

        // tags on the second record is also a serialized JSON-array string -> real JSON
        Map<String, Object> r2 = svc.fetch(source(), null, null, null).get(1);
        Object tags = r2.get("tags");
        assertTrue(tags instanceof JsonNode && ((JsonNode) tags).isArray());
        assertEquals("quarterly", ((JsonNode) tags).get(0).asText());
    }

    @Test
    void genericScalarBooleanNumericFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", CERT_1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "Manager Access Review"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("certification_name", "KF Test Targeted Cert"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Focused"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("phase", "Active"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("creator", "spadmin"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("complete", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("complete", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("total_items", "30"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("percent_complete", "100"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "focused"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("creator", "spadmin", "type", "Focused"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("creator", "spadmin", "type", "Nope"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // the four structured fields are not filterable scalars
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("certifiers", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("sign_off_history", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("allowed_statuses", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("tags", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("Manager Access Review", win.get(0).get("name"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("type", "Focused"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("type", "Focused"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
