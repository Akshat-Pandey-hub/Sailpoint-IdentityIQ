package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for SQL-report Query 2 (Workgroup Members) exposed through the shared
 * {@link NativeSqlReportRestService} — the <b>membership roster</b> endpoint {@code /kfagent/workgroup-members},
 * distinct from the native Workgroup Member entity ({@code /kfagent/workgroupmember}). The service is fed a
 * fake report source returning a canned plugin envelope (no live IIQ, no DB), and verifies: the exact
 * report id {@code workgroupMembers}; the exact seven-column contract and SELECT order; row→JSON mapping;
 * SQL NULL preserved as JSON null; the CASE-driven {@code user_status} values verbatim; exact scalar
 * filtering with AND; HTTP-400-worthy {@link IllegalArgumentException} for unknown filters / unregistered
 * reports; filtering before paging; default/explicit/invalid paging; empty {@code []}; and that Query 1
 * stays registered (no regression). {@link #COLUMNS} mirrors workgroupMembers.sql's SELECT order.
 */
class NativeWorkgroupMembersReportRestServiceTest {

    /** The 7 Query 2 columns, in the exact order of workgroupMembers.sql's SELECT. */
    private static final List<String> COLUMNS = List.of(
            "workgroup_name", "user_name", "display_name", "first_name", "last_name", "email", "user_status");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Mirrors the real server registration so "no Query 1 regression" is meaningful here.
    private final NativeSqlReportRestService svc =
            new NativeSqlReportRestService(Set.of("entitlementAssignment", "workgroupMembers"));

    private static final class CapturingSource implements NativeSqlReportRestService.ReportSource {
        private final String body;
        String requestedId;

        CapturingSource(String body) {
            this.body = body;
        }

        @Override
        public String fetchReport(String reportId) {
            this.requestedId = reportId;
            return body;
        }
    }

    /** One roster row keyed by the 7 columns. A null value models the report's SQL NULL (e.g. empty WG). */
    private static Map<String, String> row(String wg, String userName, String display, String first,
                                           String last, String email, String status) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("workgroup_name", wg);
        r.put("user_name", userName);
        r.put("display_name", display);
        r.put("first_name", first);
        r.put("last_name", last);
        r.put("email", email);
        r.put("user_status", status);
        return r;
    }

    private static String envelope(List<Map<String, String>> rows) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("report", "workgroupMembers");
        env.put("columns", COLUMNS);
        List<Map<String, Object>> jrows = new ArrayList<>();
        for (Map<String, String> r : rows) {
            jrows.add(new LinkedHashMap<>(r));
        }
        env.put("rows", jrows);
        env.put("returned", rows.size());
        env.put("truncated", Boolean.FALSE);
        try {
            return MAPPER.writeValueAsString(env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The three authoritative CASE outcomes of the report. */
    private static List<Map<String, String>> sample() {
        return List.of(
                row("IT Admins", "ada", "Ada Lovelace", "Ada", "Lovelace", "ada@corp.com", "Active"),
                row("IT Admins", "bob", "Bob Stone", "Bob", "Stone", null, "Disabled"),
                row("Empty WG", null, null, null, null, null, "No Members"));
    }

    @Test
    void requestsExactReportIdAndRejectsUnregisteredReports() {
        CapturingSource src = new CapturingSource(envelope(sample()));
        List<Map<String, Object>> rows = svc.fetch(src, "workgroupMembers", null, null, null);
        assertEquals(3, rows.size());
        assertEquals("workgroupMembers", src.requestedId, "plugin asked for the exact server report id");

        assertTrue(svc.registeredReports().contains("workgroupMembers"));
        assertTrue(svc.registeredReports().contains("entitlementAssignment"), "Query 1 still registered (no regression)");

        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "arbitrary", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), null, null, null, null));
    }

    @Test
    void exactSevenColumnContractOrderMappingNullAndStatusPreserved() {
        List<Map<String, Object>> rows =
                svc.fetch(new CapturingSource(envelope(sample())), "workgroupMembers", null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> active = rows.get(0);
        assertEquals(7, active.size(), "exactly the 7 report columns");
        assertEquals(COLUMNS, new ArrayList<>(active.keySet()), "columns in the SQL SELECT order");
        assertEquals("IT Admins", active.get("workgroup_name"));
        assertEquals("Ada Lovelace", active.get("display_name"));
        assertEquals("ada@corp.com", active.get("email"));
        assertEquals("Active", active.get("user_status"));

        Map<String, Object> disabled = rows.get(1);
        assertTrue(disabled.containsKey("email"), "null column still present");
        assertNull(disabled.get("email"), "SQL NULL email preserved as JSON null");
        assertEquals("Disabled", disabled.get("user_status"));

        Map<String, Object> noMembers = rows.get(2);
        assertNull(noMembers.get("user_name"), "empty-workgroup member columns are null");
        assertNull(noMembers.get("display_name"));
        assertEquals("No Members", noMembers.get("user_status"));

        // No KeyForge persistence metadata leaks in.
        assertFalse(active.containsKey("id"));
        assertFalse(active.containsKey("record_hash"));
        assertFalse(active.containsKey("extraction_run_id"));
        assertFalse(active.containsKey("extracted_at"));
        assertFalse(active.containsKey("src_natural_key"));
    }

    @Test
    void scalarFiltersAndAndCombine() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(2, svc.fetch(src, "workgroupMembers", Map.of("workgroup_name", "IT Admins"), null, null).size());
        assertEquals(1, svc.fetch(src, "workgroupMembers", Map.of("user_status", "Active"), null, null).size());
        // AND across two columns
        assertEquals(1, svc.fetch(src, "workgroupMembers",
                Map.of("workgroup_name", "IT Admins", "user_status", "Disabled"), null, null).size());
        assertEquals(0, svc.fetch(src, "workgroupMembers",
                Map.of("workgroup_name", "Empty WG", "user_status", "Active"), null, null).size());
        // case-sensitive
        assertEquals(0, svc.fetch(src, "workgroupMembers", Map.of("user_status", "active"), null, null).size());
    }

    @Test
    void unknownAndTechnicalFilterNamesRejected() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "workgroupMembers", Map.of("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "workgroupMembers", Map.of("record_hash", "x"), null, null));
        // a Query 1 column is not a Query 2 runtime column -> rejected
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "workgroupMembers", Map.of("entitlement_value", "x"), null, null));
    }

    @Test
    void filteringHappensBeforePaging() {
        String body = envelope(List.of(
                row("A", "u1", "U1", "f", "l", "e1", "Active"),
                row("A", "u2", "U2", "f", "l", "e2", "Disabled"),
                row("A", "u3", "U3", "f", "l", "e3", "Active"),
                row("A", "u4", "U4", "f", "l", "e4", "Active")));
        NativeSqlReportRestService.ReportSource src = id -> body;
        // filter Active -> u1,u3,u4 ; window start=1,limit=1 -> u3
        List<Map<String, Object>> win =
                svc.fetch(src, "workgroupMembers", Map.of("user_status", "Active"), 1, 1);
        assertEquals(1, win.size());
        assertEquals("u3", win.get(0).get("user_name"));
    }

    @Test
    void defaultExplicitAndInvalidPaging() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(3, svc.fetch(src, "workgroupMembers", null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(src, "workgroupMembers", null, 1, 5);
        assertEquals(2, win.size());
        assertEquals("bob", win.get(0).get("user_name"));
        assertEquals(1, svc.fetch(src, "workgroupMembers", null, 0, 1).size());
        assertEquals(3, svc.fetch(src, "workgroupMembers", null, -5, null).size()); // start<0 -> 0
        assertEquals(3, svc.fetch(src, "workgroupMembers", null, null, -1).size()); // limit<0 -> all
        assertEquals(0, svc.fetch(src, "workgroupMembers", null, 99, 5).size());    // start past end -> []
    }

    @Test
    void emptyResultReturnsEmptyArrayButStillValidatesFilterColumns() {
        String body = envelope(List.of());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertEquals(List.of(), svc.fetch(src, "workgroupMembers", null, null, null));
        assertEquals(List.of(), svc.fetch(src, "workgroupMembers", Map.of("workgroup_name", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "workgroupMembers", Map.of("bogus", "x"), null, null));
    }

    @Test
    void query1StillWorksThroughSameService() {
        // No regression: the same shared service still serves Query 1 with its own columns.
        String q1 = "{\"report\":\"entitlementAssignment\",\"columns\":[\"user_id\",\"role_name\"],"
                + "\"rows\":[{\"user_id\":\"alice\",\"role_name\":\"Admin\"}],\"returned\":1,\"truncated\":false}";
        NativeSqlReportRestService.ReportSource src = id -> q1;
        List<Map<String, Object>> rows = svc.fetch(src, "entitlementAssignment", null, null, null);
        assertEquals(1, rows.size());
        assertEquals("alice", rows.get(0).get("user_id"));
        // and a Query 2 column is unknown to Query 1
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", Map.of("user_status", "Active"), null, null));
    }

    @Test
    void restPathHasNoPostgresDependency() {
        NativeSqlReportRestService.ReportSource pureLambda = id -> envelope(sample());
        assertEquals(3, svc.fetch(pureLambda, "workgroupMembers", null, null, null).size());
    }
}
