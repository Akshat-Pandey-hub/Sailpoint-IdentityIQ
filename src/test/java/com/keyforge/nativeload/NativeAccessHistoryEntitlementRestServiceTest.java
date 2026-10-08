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
 * Contract for the KF Agent Access-History (entitlement-capture) read service: reuses the EXISTING native
 * client + envelope over a fake page source (no live IIQ, no DB — proving the REST path never needs
 * PostgreSQL), returns our 41 DB-named business fields, excludes the hist_capture_id PK + lineage, keeps the
 * booleans / ISO timestamps native and the four structured fields (compressible_property_names / capture_json /
 * attributes / extended_attributes) as real JSON — including re-hydrating capture_json from its serialized
 * string form — preserves null, and supports generic exact filtering on scalar fields only. Mirrors the two
 * supplied HistoricalEntitlementCapture rows for Alexander Evans.
 */
class NativeAccessHistoryEntitlementRestServiceTest {

    private final NativeAccessHistoryEntitlementRestService svc = new NativeAccessHistoryEntitlementRestService();

    private static final String CAP1 = "7f0001019842122981984a1286730ed8";
    private static final String CAP2 = "7f0001019842122981984a1286770ed9";

    /** The capture payload arrives on the wire as a serialized JSON STRING (the DB reads it via text()). */
    private static String captureJsonString(String eventId, String entId) {
        String capture = ("{'transformationConfigName':'AccessHistoryTransformConfig','eventId':'" + eventId
                + "','operation':'capture','eventImage':{'imageType':'identityEntitlement','imageFields':{"
                + "'attributeName':'groups','id':'" + entId + "'}}}").replace('\'', '"');
        return capture.replace("\"", "\\\"");   // escape for embedding as a JSON string value
    }

    /** Record 1: full capture, compressible_property_names array, capture_json string, null attributes. */
    private static String cap1() {
        return "{\"sourceId\":\"" + CAP1 + "\",\"entityId\":\"7f00010198421229819849ebca160c73\","
                + "\"identityId\":\"7f00010198421229819849ebc9ef0c71\",\"identityName\":\"Alexander Evans\","
                + "\"identityEntitlementId\":\"7f00010198421229819849ebca160c73\","
                + "\"applicationId\":\"7f00010198421229819849c815b90bfc\",\"applicationName\":\"EntraAuth\","
                + "\"displayValue\":\"groups\",\"attributeValue\":\"00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5\","
                + "\"type\":\"Entitlement\",\"grantedByRole\":false,\"deleted\":false,"
                + "\"effectiveDate\":\"2025-07-27T04:09:42.801Z\",\"latest\":false,\"compressed\":false,"
                + "\"brief\":false,\"full\":true,\"patch\":false,"
                + "\"smartHash\":\"C97C15A30BC24D6B27B5C64B707111D42BD7994B\","
                + "\"fullHash\":\"FE652F95D65BCEBCC87BB42286C3338FBEAA014C\","
                + "\"jsonFormat\":\"ZIPPED\",\"transformType\":\"full\","
                + "\"compressedPropertyFlag\":\"jsonFormat\",\"compressedPropertyFlagValue\":\"ZIPPED\","
                + "\"compressiblePropertyNames\":[\"json\"],"
                + "\"captureJson\":\"" + captureJsonString("d7ac462e-81ea-4c01-814f-25560dcf3cf5",
                "7f00010198421229819849ebca160c73") + "\","
                + "\"created\":\"2025-07-27T04:09:44.819Z\",\"modified\":\"2025-07-27T04:09:44.819Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.accesshistory.HistoricalEntitlementCapture\","
                + "\"extractionRunId\":\"run-1\",\"extractedAt\":\"2026-10-05T15:49:44.819Z\"}";
    }

    /** Record 2: a second capture; carries a populated attributes object to prove structured JSON handling. */
    private static String cap2() {
        return "{\"sourceId\":\"" + CAP2 + "\",\"entityId\":\"7f00010198421229819849ebca170c76\","
                + "\"identityId\":\"7f00010198421229819849ebc9ef0c71\",\"identityName\":\"Alexander Evans\","
                + "\"identityEntitlementId\":\"7f00010198421229819849ebca170c76\","
                + "\"applicationId\":\"7f00010198421229819849c815b90bfc\",\"applicationName\":\"EntraAuth\","
                + "\"displayValue\":\"groups\",\"attributeValue\":\"01ade120-f080-4096-aeb0-7fc3e023a1cc\","
                + "\"type\":\"Entitlement\",\"grantedByRole\":false,\"deleted\":false,"
                + "\"effectiveDate\":\"2025-07-27T04:09:42.801Z\",\"latest\":false,\"compressed\":false,"
                + "\"brief\":false,\"full\":true,\"patch\":false,"
                + "\"smartHash\":\"48D01C149069960B5070669DB7833CC39ADAA49E\","
                + "\"fullHash\":\"F693B2B6EA962D899F6B863487D3C1EAFFA4BB4A\","
                + "\"jsonFormat\":\"ZIPPED\",\"transformType\":\"full\","
                + "\"compressedPropertyFlag\":\"jsonFormat\",\"compressedPropertyFlagValue\":\"ZIPPED\","
                + "\"compressiblePropertyNames\":[\"json\"],"
                + "\"captureJson\":\"" + captureJsonString("a26f6c47-e977-4519-9d2e-d574d9cd7fe3",
                "7f00010198421229819849ebca170c76") + "\","
                + "\"attributes\":{\"origin\":\"aggregation\"},"
                + "\"created\":\"2025-07-27T04:09:44.824Z\",\"modified\":\"2025-07-27T04:09:44.827Z\"}";
    }

    private static NativeAccessHistoryEntitlementRestService.PageSource source() {
        return (start, limit) -> start == 0
                ? "{\"sourceCount\":2,\"rows\":[" + cap1() + "," + cap2() + "]}"
                : "{\"sourceCount\":2,\"rows\":[]}";
    }

    private static NativeAccessHistoryEntitlementRestService.PageSource emptySource() {
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
    void returnsFortyOneFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(CAP1, r.get("source_id"));
        assertEquals("7f00010198421229819849ebca160c73", r.get("entity_id"));
        assertEquals("Alexander Evans", r.get("identity_name"));
        assertEquals("EntraAuth", r.get("application_name"));
        assertEquals("groups", r.get("display_value"));
        assertEquals("Entitlement", r.get("type"));
        assertEquals("C97C15A30BC24D6B27B5C64B707111D42BD7994B", r.get("smart_hash"));
        assertEquals("ZIPPED", r.get("json_format"));
        assertEquals(41, r.size(), "exactly the 41 SailPoint-facing business fields");

        assertFalse(r.containsKey("hist_capture_id"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void booleanTimestampTypesStructuredJsonCaptureAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> r = rows.get(0);

        // booleans stay booleans (full_capture comes from wire key "full")
        assertEquals(Boolean.FALSE, r.get("granted_by_role"));
        assertEquals(Boolean.FALSE, r.get("deleted"));
        assertEquals(Boolean.FALSE, r.get("latest"));
        assertEquals(Boolean.FALSE, r.get("compressed"));
        assertEquals(Boolean.FALSE, r.get("brief"));
        assertEquals(Boolean.TRUE, r.get("full_capture"));
        assertEquals(Boolean.FALSE, r.get("patch"));
        // timestamps serialize as ISO strings
        assertEquals("2025-07-27T04:09:42.801Z", r.get("effective_date"));
        assertEquals("2025-07-27T04:09:44.819Z", r.get("created_at"));

        // compression metadata included as business fields (genuine native getters / dedicated columns)
        assertEquals("jsonFormat", r.get("compressed_property_flag"));
        assertEquals("ZIPPED", r.get("compressed_property_flag_value"));
        Object cpn = r.get("compressible_property_names");
        assertTrue(cpn instanceof JsonNode && ((JsonNode) cpn).isArray(), "compressible_property_names is JSON array");
        assertEquals("json", ((JsonNode) cpn).get(0).asText());

        // capture_json re-hydrated to STRUCTURED JSON (not an escaped string), full payload preserved
        Object capture = r.get("capture_json");
        assertTrue(capture instanceof JsonNode && ((JsonNode) capture).isObject(),
                "capture_json must be structured JSON, not a stringified blob");
        JsonNode c = (JsonNode) capture;
        assertEquals("capture", c.get("operation").asText());
        assertEquals("d7ac462e-81ea-4c01-814f-25560dcf3cf5", c.get("eventId").asText());
        assertEquals("groups", c.get("eventImage").get("imageFields").get("attributeName").asText());

        // nulls preserved exactly
        assertNull(r.get("name"));
        assertNull(r.get("entity_name"));
        assertNull(r.get("native_identity"));
        assertNull(r.get("attribute_name"));
        assertNull(r.get("role_id"));
        assertNull(r.get("request_item_id"));
        assertNull(r.get("certification_item_id"));
        assertNull(r.get("extended_to_date"));
        assertNull(r.get("patch_doc_parent"));
        assertNull(r.get("attributes"), "null structured field preserved");
        assertNull(r.get("extended_attributes"));

        // second record: attributes present as a real JSON object
        Object attrs = rows.get(1).get("attributes");
        assertTrue(attrs instanceof JsonNode && ((JsonNode) attrs).isObject());
        assertEquals("aggregation", ((JsonNode) attrs).get("origin").asText());
    }

    @Test
    void genericScalarAndBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", CAP1), null, null).size());
        assertEquals(2, svc.fetch(source(), f("identity_name", "Alexander Evans"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("application_name", "EntraAuth"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("type", "Entitlement"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("full_capture", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("granted_by_role", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("smart_hash", "48D01C149069960B5070669DB7833CC39ADAA49E"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("attribute_value", "00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "entitlement"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("identity_name", "Alexander Evans", "source_id", CAP2), null, null).size());
        assertEquals(0, svc.fetch(source(), f("identity_name", "Alexander Evans", "source_id", "nope"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("hist_capture_id", "x"), null, null));
        // the four structured JSON fields are not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("capture_json", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("attributes", "{}"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("extended_attributes", "{}"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("compressible_property_names", "[]"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(CAP2, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("type", "Entitlement"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Entitlement"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
