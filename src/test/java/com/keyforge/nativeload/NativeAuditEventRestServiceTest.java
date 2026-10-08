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
 * Contract for the KF Agent Audit-Event read service: reuses the EXISTING native (append-only) import over a
 * fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 19
 * DB-named business fields, excludes the auditid PK + lineage, keeps {@code attributes} as real JSON while
 * {@code created_at} is ISO, preserves null, and supports generic exact filtering on scalar fields only.
 * Mirrors the two supplied ServerUpDown audit rows. AuditEvent is append-only (no is_deleted/deleted_at).
 */
class NativeAuditEventRestServiceTest {

    private final NativeAuditEventRestService svc = new NativeAuditEventRestService();

    private static final String DOWN = "7f0001019eb91d3c819f06cf65c20836";
    private static final String UP = "7f0001019f061fdc819f06cff4480000";

    /** Sample 1: ServerDown — attributes {}, most fields null, created timestamp. */
    private static String serverDown() {
        return "{\"sourceId\":\"" + DOWN + "\",\"action\":\"ServerUpDown\",\"auditSource\":\"keyforgeiga\","
                + "\"target\":\"ServerDown\",\"interfaceName\":\"keyforgeiga\",\"attributes\":{},"
                + "\"created\":\"2026-06-27T02:01:23.138Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.AuditEvent\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-09-24T20:47:52.031Z\"}";
    }

    /** Sample 2: ServerUp — a populated attributes object to prove structured JSON preservation. */
    private static String serverUp() {
        return "{\"sourceId\":\"" + UP + "\",\"action\":\"ServerUpDown\",\"auditSource\":\"keyforgeiga\","
                + "\"target\":\"ServerUp\",\"interfaceName\":\"keyforgeiga\","
                + "\"attributes\":{\"host\":\"node-1\",\"pid\":1234},"
                + "\"created\":\"2026-06-27T02:01:59.626Z\"}";
    }

    private static NativeAuditEventPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + serverDown() + "," + serverUp() + "]}" : "{\"rows\":[]}";
    }

    private static NativeAuditEventPageSource emptySource() {
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
        assertEquals(DOWN, r.get("source_id"));
        assertEquals("ServerUpDown", r.get("action"));
        assertEquals("keyforgeiga", r.get("audit_source"));
        assertEquals("ServerDown", r.get("target"));
        assertEquals("keyforgeiga", r.get("interface_name"));
        assertEquals(19, r.size(), "exactly the 19 SailPoint-facing business fields");

        assertFalse(r.containsKey("auditid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        // append-only: these never existed, but assert they are not fabricated either
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void attributesIsJsonCreatedIsIsoAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> down = rows.get(0);

        // attributes is jsonb -> real JSON object (empty {} preserved, not stringified)
        assertTrue(down.get("attributes") instanceof JsonNode, "attributes is jsonb -> JSON");
        assertTrue(((JsonNode) down.get("attributes")).isObject());
        assertEquals(0, ((JsonNode) down.get("attributes")).size());
        // created_at serialized as ISO string
        assertEquals("2026-06-27T02:01:23.138Z", down.get("created_at"));
        // unset text fields stay null
        assertNull(down.get("application"));
        assertNull(down.get("account_name"));
        assertNull(down.get("instance"));
        assertNull(down.get("attribute_name"));
        assertNull(down.get("attribute_value"));
        assertNull(down.get("server_host"));
        assertNull(down.get("client_host"));
        assertNull(down.get("tracking_id"));
        assertNull(down.get("string1"));
        assertNull(down.get("string4"));

        // populated attributes object preserved structurally
        JsonNode attrs = (JsonNode) rows.get(1).get("attributes");
        assertEquals("node-1", attrs.get("host").asText());
        assertEquals(1234, attrs.get("pid").asInt());
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", DOWN), null, null).size());
        assertEquals(1, svc.fetch(source(), f("target", "ServerUp"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("action", "ServerUpDown"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("audit_source", "keyforgeiga"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("interface_name", "keyforgeiga"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("target", "serverup"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("action", "ServerUpDown", "target", "ServerDown"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("action", "ServerUpDown", "target", "Nope"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("auditid", "x"), null, null));
        // attributes jsonb is not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("attributes", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("ServerUp", win.get(0).get("target"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("action", "ServerUpDown"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("target", "ServerDown"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
