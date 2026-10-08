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
 * Contract for the KF Agent Access-History identity-event read service: reuses the EXISTING native client +
 * envelope over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL),
 * returns our 38 DB-named business fields, excludes the hist_event_id PK + lineage, keeps event_date/
 * created_at/modified_at ISO (modified_at nullable), re-hydrates event_detail_json (text column holding the
 * raw event payload) to structured JSON, preserves null, keeps qry_property1..10, and supports generic exact
 * filtering on scalar fields only. Mirrors the two supplied IdentityDiscovered events + a detail-carrying one.
 */
class NativeAccessHistoryIdentityEventRestServiceTest {

    private final NativeAccessHistoryIdentityEventRestService svc = new NativeAccessHistoryIdentityEventRestService();

    private static final String EV1 = "7f0001019714176981971956c14808ec";
    private static final String EV2 = "7f0001019842122981984a1285a30ebd";
    private static final String EV3 = "7f0001019842122981984a1285a30e99";

    /** eventDetailJson arrives as a serialized JSON STRING on the wire (DB reads it via text()). */
    private static String detailJsonString() {
        String detail = ("{'property':'manager','oldValue':null,'newValue':'spadmin'}").replace('\'', '"');
        return detail.replace("\"", "\\\"");
    }

    /** Record 1: spadmin IdentityDiscovered — most fields null (as the sample shows). */
    private static String ev1() {
        return "{\"sourceId\":\"" + EV1 + "\",\"entityId\":\"7f000101971416688197147684ad00ff\","
                + "\"entityName\":\"spadmin\",\"definedEntityName\":\"spadmin\","
                + "\"eventType\":\"IdentityDiscovered\",\"eventCategory\":\"Identity\","
                + "\"eventSourceType\":\"INITIAL_CAPTURE\",\"eventDate\":\"2025-05-29T00:00:00.113Z\","
                + "\"prevCaptureId\":\"7f0001019714176981971956c14708eb\","
                + "\"created\":\"2025-05-29T00:00:05.448Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.accesshistory.HistoricalIdentityEvent\","
                + "\"extractionRunId\":\"run-1\",\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** Record 2: Alexander Cook IdentityDiscovered. */
    private static String ev2() {
        return "{\"sourceId\":\"" + EV2 + "\",\"entityId\":\"7f00010198421229819849ebca370c93\","
                + "\"entityName\":\"Alexander Cook\",\"definedEntityName\":\"Alexander Cook\","
                + "\"eventType\":\"IdentityDiscovered\",\"eventCategory\":\"Identity\","
                + "\"eventSourceType\":\"INITIAL_CAPTURE\",\"eventDate\":\"2025-07-27T04:09:42.801Z\","
                + "\"prevCaptureId\":\"7f0001019842122981984a12859f0ebc\","
                + "\"created\":\"2025-07-27T04:09:44.611Z\"}";
    }

    /** Record 3: an attribute-change event with a populated event_detail_json + qry properties + modified. */
    private static String ev3() {
        return "{\"sourceId\":\"" + EV3 + "\",\"entityId\":\"7f00010198421229819849ebca370c93\","
                + "\"entityName\":\"Alexander Cook\",\"eventType\":\"AttributeChanged\","
                + "\"eventCategory\":\"Identity\",\"eventSourceType\":\"AGGREGATION\","
                + "\"eventDate\":\"2025-07-28T04:00:00Z\",\"propertyName\":\"manager\","
                + "\"oldValue\":\"\",\"newValue\":\"spadmin\",\"auditEventId\":\"aud-1\","
                + "\"eventDetailJson\":\"" + detailJsonString() + "\","
                + "\"qryProperty1\":\"q1\",\"qryProperty10\":\"q10\","
                + "\"created\":\"2025-07-28T04:00:05Z\",\"modified\":\"2025-07-28T04:00:06Z\"}";
    }

    private static NativeAccessHistoryIdentityEventRestService.PageSource source() {
        return (start, limit) -> start == 0
                ? "{\"sourceCount\":3,\"rows\":[" + ev1() + "," + ev2() + "," + ev3() + "]}"
                : "{\"sourceCount\":3,\"rows\":[]}";
    }

    private static NativeAccessHistoryIdentityEventRestService.PageSource emptySource() {
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
    void returnsThirtyEightFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(EV1, r.get("source_id"));
        assertEquals("7f000101971416688197147684ad00ff", r.get("entity_id"));
        assertEquals("spadmin", r.get("entity_name"));
        assertEquals("spadmin", r.get("defined_entity_name"));
        assertEquals("IdentityDiscovered", r.get("event_type"));
        assertEquals("Identity", r.get("event_category"));
        assertEquals("INITIAL_CAPTURE", r.get("event_source_type"));
        assertEquals("7f0001019714176981971956c14708eb", r.get("prev_capture_id"));
        assertTrue(r.containsKey("qry_property1"));
        assertTrue(r.containsKey("qry_property10"));
        assertEquals(38, r.size(), "exactly the 38 SailPoint-facing business fields");

        assertFalse(r.containsKey("hist_event_id"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void timestampTypesStructuredDetailAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> r = rows.get(0);

        // timestamps serialize as ISO strings; modified_at is genuinely nullable
        assertEquals("2025-05-29T00:00:00.113Z", r.get("event_date"));
        assertEquals("2025-05-29T00:00:05.448Z", r.get("created_at"));
        assertNull(r.get("modified_at"), "modified_at is null for this event");

        // IdentityDiscovered events: most fields null (preserved, not manufactured)
        assertNull(r.get("name"));
        assertNull(r.get("event_detail_json"));
        assertNull(r.get("capture_id"));
        assertNull(r.get("audit_event_id"));
        assertNull(r.get("property_name"));
        assertNull(r.get("old_value"));
        assertNull(r.get("account_id"));
        assertNull(r.get("acct_app_name"));
        assertNull(r.get("request_item_id"));
        assertNull(r.get("qry_property1"));
        assertNull(r.get("qry_property10"));

        // event_detail_json re-hydrated to STRUCTURED JSON on the detail-carrying event
        Map<String, Object> ev3 = rows.get(2);
        Object detail = ev3.get("event_detail_json");
        assertTrue(detail instanceof JsonNode && ((JsonNode) detail).isObject(),
                "event_detail_json must be structured JSON, not an escaped string");
        assertEquals("manager", ((JsonNode) detail).get("property").asText());
        assertEquals("spadmin", ((JsonNode) detail).get("newValue").asText());
        // qry properties + modified preserved on ev3
        assertEquals("q1", ev3.get("qry_property1"));
        assertEquals("q10", ev3.get("qry_property10"));
        assertEquals("2025-07-28T04:00:06Z", ev3.get("modified_at"));
        assertEquals("manager", ev3.get("property_name"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", EV2), null, null).size());
        assertEquals(1, svc.fetch(source(), f("entity_name", "spadmin"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("event_type", "IdentityDiscovered"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("event_type", "AttributeChanged"), null, null).size());
        assertEquals(3, svc.fetch(source(), f("event_category", "Identity"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("event_source_type", "INITIAL_CAPTURE"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("property_name", "manager"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("event_type", "identitydiscovered"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("event_category", "Identity", "entity_name", "Alexander Cook",
                "event_type", "IdentityDiscovered"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("event_type", "IdentityDiscovered", "property_name", "manager"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("hist_event_id", "x"), null, null));
        // event_detail_json is structured JSON -> not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("event_detail_json", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(EV2, win.get(0).get("source_id"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("event_type", "IdentityDiscovered"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("event_type", "IdentityDiscovered"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
