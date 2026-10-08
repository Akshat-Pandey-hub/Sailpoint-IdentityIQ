package com.keyforge.nativeload;

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
 * Contract for the KF Agent Workgroup-Member read service: reuses the native membership client + parser over a
 * fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), replays the cursor
 * loop ({@code nextStart}/{@code done}), returns our 7 native business fields — including the record-only
 * {@code workgroup_name} the DB table doesn't persist and the native SailPoint ids (NOT the canonical-UUID DB
 * columns) — keeps member_is_workgroup boolean, preserves null, and supports generic exact filtering on every
 * scalar field. The preview may have 0 members (→ []). The field-count assertions fail if any genuine field
 * is accidentally omitted.
 */
class NativeWorkgroupMemberRestServiceTest {

    private final NativeWorkgroupMemberRestService svc = new NativeWorkgroupMemberRestService();

    private static String member(String wgId, String wgName, String idId, String idName,
                                 String first, String last, boolean memberIsWg) {
        StringBuilder b = new StringBuilder("{\"workgroupId\":\"" + wgId + "\",\"workgroupName\":\"" + wgName
                + "\",\"identityId\":\"" + idId + "\",\"identityName\":\"" + idName + "\",");
        b.append(first == null ? "\"firstName\":null," : "\"firstName\":\"" + first + "\",");
        b.append(last == null ? "\"lastName\":null," : "\"lastName\":\"" + last + "\",");
        b.append("\"memberIsWorkgroup\":").append(memberIsWg).append(",");
        // lineage — must NOT appear in the response
        b.append("\"srcSystem\":\"IdentityIQ\",\"extractionRunId\":\"run-1\",\"extractedAt\":\"2026-10-08T00:00:00Z\"}");
        return b.toString();
    }

    /** One page, done=true, with two members of wg-1 and a nested-workgroup member of wg-2. */
    private static NativeWorkgroupMemberRestService.PageSource source() {
        String rows = member("wg-1", "Security Admins", "id-1", "Alexander Evans", "Alexander", "Evans", false)
                + "," + member("wg-1", "Security Admins", "id-2", "Alexander Cook", "Alexander", "Cook", false)
                + "," + member("wg-2", "Nested Group", "wg-3", "Sub Group", null, null, true);
        return (start, limit) -> start == 0
                ? "{\"rows\":[" + rows + "],\"nextStart\":-1,\"done\":true}"
                : "{\"rows\":[],\"nextStart\":-1,\"done\":true}";
    }

    /** Two-page cursor to prove the loop advances on nextStart until done. */
    private static NativeWorkgroupMemberRestService.PageSource pagedSource() {
        return (start, limit) -> {
            if (start == 0) {
                return "{\"rows\":[" + member("wg-1", "Security Admins", "id-1", "Alexander Evans", "Alexander", "Evans", false)
                        + "],\"nextStart\":1,\"done\":false}";
            }
            return "{\"rows\":[" + member("wg-1", "Security Admins", "id-2", "Alexander Cook", "Alexander", "Cook", false)
                    + "],\"nextStart\":-1,\"done\":true}";
        };
    }

    private static NativeWorkgroupMemberRestService.PageSource emptySource() {
        return (start, limit) -> "{\"rows\":[],\"nextStart\":-1,\"done\":true}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsSevenNativeFieldsIncludingWorkgroupNameAndExcludesTechnical() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals("wg-1", r.get("workgroup_id"));              // native SailPoint workgroup id
        assertEquals("Security Admins", r.get("workgroup_name")); // record-only native field (no DB column)
        assertEquals("id-1", r.get("identity_id"));               // native SailPoint identity id
        assertEquals("Alexander Evans", r.get("member_name"));
        assertEquals("Alexander", r.get("first_name"));
        assertEquals("Evans", r.get("last_name"));
        assertEquals(Boolean.FALSE, r.get("member_is_workgroup"));
        // every genuine field must be present — this fails if one is dropped
        assertTrue(r.containsKey("workgroup_id") && r.containsKey("workgroup_name") && r.containsKey("identity_id")
                && r.containsKey("member_name") && r.containsKey("first_name") && r.containsKey("last_name")
                && r.containsKey("member_is_workgroup"));
        assertEquals(7, r.size(), "exactly the 7 SailPoint-facing native fields");

        // technical / canonical / lineage / soft-delete columns excluded
        assertFalse(r.containsKey("id"));                 // KeyForge deterministic edge PK
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("src_system"));
        assertFalse(r.containsKey("src_interface"));
        assertFalse(r.containsKey("src_object_type"));
        assertFalse(r.containsKey("src_object_id"));
        assertFalse(r.containsKey("src_natural_key"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void booleanTypeAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        // nested-workgroup member: member_is_workgroup true, first/last name null, workgroup_name present
        Map<String, Object> nested = rows.get(2);
        assertEquals(Boolean.TRUE, nested.get("member_is_workgroup"));
        assertEquals("Nested Group", nested.get("workgroup_name"));
        assertEquals("wg-3", nested.get("identity_id"));
        assertNull(nested.get("first_name"), "null first_name preserved");
        assertNull(nested.get("last_name"), "null last_name preserved");
    }

    @Test
    void cursorLoopAdvancesAcrossPages() {
        List<Map<String, Object>> rows = svc.fetch(pagedSource(), null, null, null);
        assertEquals(2, rows.size(), "both pages collected via nextStart");
        assertEquals("id-1", rows.get(0).get("identity_id"));
        assertEquals("id-2", rows.get(1).get("identity_id"));
    }

    @Test
    void genericScalarAndBooleanFilters() {
        assertEquals(2, svc.fetch(source(), f("workgroup_id", "wg-1"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("workgroup_name", "Security Admins"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("identity_id", "id-2"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("member_name", "Alexander Cook"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("first_name", "Alexander"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("last_name", "Evans"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("member_is_workgroup", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("member_is_workgroup", "false"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("workgroup_name", "security admins"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("workgroup_id", "wg-1", "member_name", "Alexander Evans"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("workgroup_id", "wg-1", "member_is_workgroup", "true"), null, null).size());
    }

    @Test
    void unknownAndTechnicalFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("id", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("record_hash", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("src_object_id", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(3, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("id-2", win.get(0).get("identity_id"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("workgroup_id", "wg-1"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("workgroup_id", "wg-1"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
