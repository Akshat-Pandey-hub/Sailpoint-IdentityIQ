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
 * Contract for the KF Agent Access-History ROLE-event read service
 * ({@code sailpoint.object.accesshistory.HistoricalRoleEvent}): reuses the EXISTING native client + envelope
 * over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns the
 * same 38 DB-named business fields as the identity-event endpoint (HistoricalRoleEvent shares the
 * HistoricalEvent/QueryableAccessHistory field contract), but is a role-centric view ({@code entity_id}/
 * {@code entity_name} are the Role). Excludes the hist_event_id PK + lineage, keeps event_date/created_at/
 * modified_at ISO (modified_at nullable), re-hydrates event_detail_json to structured JSON, preserves nulls,
 * keeps qry_property1..10, and filters on scalar fields only.
 */
class NativeAccessHistoryRoleEventRestServiceTest {

    private final NativeAccessHistoryRoleEventRestService svc = new NativeAccessHistoryRoleEventRestService();

    private static final String EV1 = "8a0001019714176981971956c14808a1";
    private static final String EV2 = "8a0001019842122981984a1285a30eb2";
    private static final String EV3 = "8a0001019842122981984a1285a30e33";

    /** eventDetailJson arrives as a serialized JSON STRING on the wire (DB reads it via text()). */
    private static String detailJsonString() {
        String detail = ("{'property':'disabled','oldValue':'true','newValue':'false'}").replace('\'', '"');
        return detail.replace("\"", "\\\"");
    }

    /** Record 1: a role creation event — most fields null. entity is the Role. */
    private static String ev1() {
        return "{\"sourceId\":\"" + EV1 + "\",\"entityId\":\"8a000101971416688197147684ad00a1\","
                + "\"entityName\":\"IT Administrator\",\"definedEntityName\":\"IT Administrator\","
                + "\"eventType\":\"RoleCreated\",\"eventCategory\":\"Role\","
                + "\"eventSourceType\":\"INITIAL_CAPTURE\",\"eventDate\":\"2025-05-29T00:00:00.113Z\","
                + "\"prevCaptureId\":\"8a0001019714176981971956c14708a0\","
                + "\"created\":\"2025-05-29T00:00:05.448Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.accesshistory.HistoricalRoleEvent\","
                + "\"extractionRunId\":\"run-1\",\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** Record 2: another role creation event. */
    private static String ev2() {
        return "{\"sourceId\":\"" + EV2 + "\",\"entityId\":\"8a00010198421229819849ebca370c22\","
                + "\"entityName\":\"Finance Manager\",\"definedEntityName\":\"Finance Manager\","
                + "\"eventType\":\"RoleCreated\",\"eventCategory\":\"Role\","
                + "\"eventSourceType\":\"INITIAL_CAPTURE\",\"eventDate\":\"2025-07-27T04:09:42.801Z\","
                + "\"prevCaptureId\":\"8a0001019842122981984a12859f0eb2\","
                + "\"created\":\"2025-07-27T04:09:44.611Z\"}";
    }

    /** Record 3: a role attribute-change event with a populated event_detail_json + qry properties + modified. */
    private static String ev3() {
        return "{\"sourceId\":\"" + EV3 + "\",\"entityId\":\"8a00010198421229819849ebca370c22\","
                + "\"entityName\":\"Finance Manager\",\"eventType\":\"AttributeChanged\","
                + "\"eventCategory\":\"Role\",\"eventSourceType\":\"AGGREGATION\","
                + "\"eventDate\":\"2025-07-28T04:00:00Z\",\"propertyName\":\"disabled\","
                + "\"oldValue\":\"true\",\"newValue\":\"false\",\"auditEventId\":\"aud-r1\","
                + "\"eventDetailJson\":\"" + detailJsonString() + "\","
                + "\"qryProperty1\":\"q1\",\"qryProperty10\":\"q10\","
                + "\"created\":\"2025-07-28T04:00:05Z\",\"modified\":\"2025-07-28T04:00:06Z\"}";
    }

    private static NativeAccessHistoryRoleEventRestService.PageSource source() {
        return (start, limit) -> start == 0
                ? "{\"sourceCount\":3,\"rows\":[" + ev1() + "," + ev2() + "," + ev3() + "]}"
                : "{\"sourceCount\":3,\"rows\":[]}";
    }

    private static NativeAccessHistoryRoleEventRestService.PageSource emptySource() {
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
        assertEquals("8a000101971416688197147684ad00a1", r.get("entity_id"));
        assertEquals("IT Administrator", r.get("entity_name"), "role-centric: entity is the Role");
        assertEquals("RoleCreated", r.get("event_type"));
        assertEquals("Role", r.get("event_category"));
        assertEquals("INITIAL_CAPTURE", r.get("event_source_type"));
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

        assertEquals("2025-05-29T00:00:00.113Z", r.get("event_date"));
        assertEquals("2025-05-29T00:00:05.448Z", r.get("created_at"));
        assertNull(r.get("modified_at"), "modified_at is null for this event");

        assertNull(r.get("name"));
        assertNull(r.get("event_detail_json"));
        assertNull(r.get("property_name"));
        assertNull(r.get("old_value"));
        assertNull(r.get("qry_property1"));

        // event_detail_json re-hydrated to STRUCTURED JSON on the detail-carrying event
        Map<String, Object> ev3 = rows.get(2);
        Object detail = ev3.get("event_detail_json");
        assertTrue(detail instanceof JsonNode && ((JsonNode) detail).isObject(),
                "event_detail_json must be structured JSON, not an escaped string");
        assertEquals("disabled", ((JsonNode) detail).get("property").asText());
        assertEquals("false", ((JsonNode) detail).get("newValue").asText());
        assertEquals("q1", ev3.get("qry_property1"));
        assertEquals("q10", ev3.get("qry_property10"));
        assertEquals("2025-07-28T04:00:06Z", ev3.get("modified_at"));
        assertEquals("disabled", ev3.get("property_name"));
    }

    @Test
    void genericScalarFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", EV2), null, null).size());
        assertEquals(1, svc.fetch(source(), f("entity_name", "IT Administrator"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("event_type", "RoleCreated"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("event_type", "AttributeChanged"), null, null).size());
        assertEquals(3, svc.fetch(source(), f("event_category", "Role"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("property_name", "disabled"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("event_type", "rolecreated"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("event_category", "Role", "entity_name", "Finance Manager",
                "event_type", "RoleCreated"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("event_type", "RoleCreated", "property_name", "disabled"), null, null).size());
    }

    @Test
    void unknownAndStructuredFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("hist_event_id", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("event_detail_json", "{}"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals(EV2, win.get(0).get("source_id"));
        assertEquals(2, svc.fetch(source(), f("event_type", "RoleCreated"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("event_type", "RoleCreated"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
