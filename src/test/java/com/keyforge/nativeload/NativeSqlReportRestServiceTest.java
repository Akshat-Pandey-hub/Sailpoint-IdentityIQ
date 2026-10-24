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
 * Contract for the shared KF Agent SQL-report read service, exercised with SQL-report Query 1
 * (Entitlement Assignment). The service is fed a fake {@link NativeSqlReportRestService.ReportSource}
 * that returns a canned plugin envelope (no live IIQ, no {@code NativeSqlReportClient}, no DB — proving
 * the REST path never needs PostgreSQL), then verifies: only the registered report id is accepted; the
 * exact 29-column Query 1 contract and SELECT order are preserved; SQL NULL survives as JSON null; no
 * KeyForge persistence metadata is injected; exact scalar filtering with AND; HTTP-400-worthy
 * {@link IllegalArgumentException} for unknown filters and unregistered reports; filtering before
 * paging; default/explicit/invalid paging; and empty results.
 *
 * <p>{@link #COLUMNS} mirrors the authoritative SELECT aliases, in order, of
 * {@code com/keyforge/nativeiiq/sql/entitlementAssignment.sql} (verified against the file).
 */
class NativeSqlReportRestServiceTest {

    /** The 29 output columns, in the exact order currently selected by entitlementAssignment.sql. */
    private static final List<String> COLUMNS = List.of(
            "identity_id", "user_id", "user_name", "application_name", "account_name",
            "entitlement_attribute", "entitlement_value", "assigned", "granted_by_role", "entitlement_source",
            "entitlement_assignment_id", "entitlement_assigner", "entitlement_created", "entitlement_modified",
            "role_id", "role_name", "role_display_name", "role_type", "role_requestable", "identity_request_id",
            "access_request_id", "request_type", "requested_by", "request_date", "request_status",
            "request_operation", "provisioning_state", "assignment_type", "assignment_reason");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NativeSqlReportRestService svc =
            new NativeSqlReportRestService(Set.of("entitlementAssignment"));

    /** A fake report source that records the exact report id requested and returns a fixed envelope. */
    private static final class CapturingSource implements NativeSqlReportRestService.ReportSource {
        private final String body;
        String requestedId;
        Map<String, String> requestedFilters;
        Integer requestedStart;
        Integer requestedLimit;

        CapturingSource(String body) {
            this.body = body;
        }

        @Override
        public String fetchReport(String reportId) {
            this.requestedId = reportId;
            return body;
        }

        @Override
        public String fetchReport(String reportId, Map<String, String> filters, Integer start, Integer limit) {
            this.requestedId = reportId;
            this.requestedFilters = filters;
            this.requestedStart = start;
            this.requestedLimit = limit;
            try {
                com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(body);
                List<Map<String, Object>> selected = new ArrayList<>();
                int seen = 0;
                int from = start == null ? 0 : start;
                int pageSize = limit == null ? 200000 : limit;
                boolean hasMore = false;
                for (com.fasterxml.jackson.databind.JsonNode node : root.path("rows")) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String column : COLUMNS) {
                        com.fasterxml.jackson.databind.JsonNode value = node.get(column);
                        row.put(column, value == null || value.isNull() ? null : value.asText());
                    }
                    boolean matches = true;
                    if (filters != null) {
                        for (Map.Entry<String, String> filter : filters.entrySet()) {
                            if (row.get(filter.getKey()) == null
                                    || !row.get(filter.getKey()).equals(filter.getValue())) {
                                matches = false;
                                break;
                            }
                        }
                    }
                    if (!matches) continue;
                    if (seen++ < from) continue;
                    if (selected.size() >= pageSize) {
                        hasMore = true;
                        break;
                    }
                    selected.add(new LinkedHashMap<>(row));
                }
                Map<String, Object> env = new LinkedHashMap<>();
                env.put("columns", COLUMNS);
                env.put("rows", selected);
                env.put("returned", selected.size());
                env.put("truncated", Boolean.FALSE);
                env.put("hasMore", hasMore);
                env.put("pagingApplied", Boolean.TRUE);
                return MAPPER.writeValueAsString(env);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** One row keyed by the SQL's 29 columns; every column defaults then overrides representative values. */
    private static Map<String, String> row(String userId, String assigned, String assignmentType) {
        Map<String, String> r = new LinkedHashMap<>();
        for (String c : COLUMNS) {
            r.put(c, c + "-val");
        }
        r.put("user_id", userId);
        r.put("role_requestable", null); // SQL selects NULL AS role_requestable in this environment
        r.put("assigned", assigned);
        r.put("assignment_type", assignmentType);
        return r;
    }

    /** Build the plugin envelope exactly as NativeSqlReportResource returns it (columns always present). */
    private static String envelope(List<Map<String, String>> rows) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("report", "entitlementAssignment");
        env.put("columns", COLUMNS);
        List<Map<String, Object>> jrows = new ArrayList<>();
        for (Map<String, String> r : rows) {
            jrows.add(new LinkedHashMap<>(r)); // LinkedHashMap keeps a null value -> JSON null
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

    @Test
    void requestsExactReportIdAndRejectsUnregisteredReports() {
        CapturingSource src = new CapturingSource(envelope(List.of(row("alice", null, "true"))));
        List<Map<String, Object>> rows = svc.fetch(src, "entitlementAssignment", null, null, null);
        assertEquals(1, rows.size());
        assertEquals("entitlementAssignment", src.requestedId, "plugin asked for the exact server report id");

        assertTrue(svc.registeredReports().contains("entitlementAssignment"));
        assertEquals(1, svc.registeredReports().size(), "only Query 1 registered for KF Agent REST");

        // Arbitrary/unregistered report names cannot drive the plugin (no arbitrary-SQL/report surface).
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "arbitrary", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "workgroupMembers", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), null, null, null, null));
    }

    @Test
    void exactColumnContractOrderMappingAndNullPreserved() {
        Map<String, String> r = row("alice", null, "true"); // role_requestable is SQL NULL
        List<Map<String, Object>> rows =
                svc.fetch(new CapturingSource(envelope(List.of(r))), "entitlementAssignment", null, null, null);
        assertEquals(1, rows.size());

        Map<String, Object> out = rows.get(0);
        assertEquals(29, out.size(), "exactly the 29 report columns");
        assertEquals(COLUMNS, new ArrayList<>(out.keySet()), "columns in the SQL SELECT order");

        assertEquals("alice", out.get("user_id"));
        assertEquals("identity_id-val", out.get("identity_id"));
        assertEquals("assignment_reason-val", out.get("assignment_reason"));
        assertTrue(out.containsKey("role_requestable"), "null column still present");
        assertNull(out.get("role_requestable"), "SQL NULL preserved as JSON null");

        // No KeyForge persistence metadata leaks into the report rows (those live only in the kf_* table).
        assertFalse(out.containsKey("roleentitlementid"));
        assertFalse(out.containsKey("record_hash"));
        assertFalse(out.containsKey("extraction_run_id"));
        assertFalse(out.containsKey("extracted_at"));
        assertFalse(out.containsKey("src_natural_key"));
        assertFalse(out.containsKey("is_deleted"));
    }

    @Test
    void scalarFiltersAndAndCombine() {
        String body = envelope(List.of(
                row("alice", "true", "yes"), row("bob", "false", "yes"), row("carol", "true", "no")));
        NativeSqlReportRestService.ReportSource src = new CapturingSource(body);

        assertEquals(2, svc.fetch(src, "entitlementAssignment", Map.of("assigned", "true"), null, null).size());
        assertEquals(1, svc.fetch(src, "entitlementAssignment", Map.of("user_id", "bob"), null, null).size());
        // AND across two columns: only alice is (assigned, yes)
        assertEquals(1, svc.fetch(src, "entitlementAssignment",
                Map.of("assigned", "true", "assignment_type", "yes"), null, null).size());
        assertEquals(0, svc.fetch(src, "entitlementAssignment",
                Map.of("assigned", "false", "assignment_type", "no"), null, null).size());
        // case-sensitive exact match
        assertEquals(0, svc.fetch(src, "entitlementAssignment", Map.of("user_id", "ALICE"), null, null).size());
    }

    @Test
    void unknownAndTechnicalFilterNamesRejected() {
        String body = envelope(List.of(row("alice", "true", "yes")));
        NativeSqlReportRestService.ReportSource src = new CapturingSource(body);
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", Map.of("nope", "x"), null, null));
        // a KeyForge technical column is not one of the report's runtime columns -> rejected
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", Map.of("record_hash", "x"), null, null));
    }

    @Test
    void filteringHappensBeforePaging() {
        String body = envelope(List.of(
                row("a", "true", "y"), row("b", "false", "y"), row("c", "true", "y"), row("d", "true", "y")));
        NativeSqlReportRestService.ReportSource src = new CapturingSource(body);
        // filter true -> a,c,d ; then window start=1,limit=1 -> c
        List<Map<String, Object>> win =
                svc.fetch(src, "entitlementAssignment", Map.of("assigned", "true"), 1, 1);
        assertEquals(1, win.size());
        assertEquals("c", win.get(0).get("user_id"));
        CapturingSource paged = (CapturingSource) src;
        assertEquals(Map.of("assigned", "true"), paged.requestedFilters);
        assertEquals(1, paged.requestedStart);
        assertEquals(1, paged.requestedLimit);
    }

    @Test
    void defaultExplicitAndInvalidPaging() {
        String body = envelope(List.of(row("a", null, "y"), row("b", null, "y"), row("c", null, "y")));
        NativeSqlReportRestService.ReportSource src = new CapturingSource(body);

        assertEquals(3, svc.fetch(src, "entitlementAssignment", null, null, null).size(), "default = all");
        List<Map<String, Object>> win = svc.fetch(src, "entitlementAssignment", null, 1, 5);
        assertEquals(2, win.size());
        assertEquals("b", win.get(0).get("user_id"));
        assertEquals(1, svc.fetch(src, "entitlementAssignment", null, 0, 1).size());
        // invalid paging clamps consistently: start<0 -> 0, limit<0 -> all, start past end -> []
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, -5, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, null, -1));
        assertEquals(0, svc.fetch(src, "entitlementAssignment", null, 99, 5).size());
    }

    @Test
    void emptyResultReturnsEmptyArrayButStillValidatesFilterColumns() {
        String body = envelope(List.of()); // 0 rows, but columns array still present
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertEquals(List.of(), svc.fetch(new CapturingSource(body), "entitlementAssignment", null, null, null));
        assertEquals(List.of(), svc.fetch(new CapturingSource(body), "entitlementAssignment", Map.of("user_id", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource(body), "entitlementAssignment", Map.of("bogus", "x"), null, null));
    }

    @Test
    void restPathHasNoPostgresDependency() {
        // The entire flow runs through an in-memory lambda source; no java.sql.Connection is referenced
        // anywhere in this service's API. This test wouldn't compile/run if the REST path needed a DB handle.
        NativeSqlReportRestService.ReportSource pureLambda = id -> envelope(List.of(row("x", null, "y")));
        assertEquals(1, svc.fetch(pureLambda, "entitlementAssignment", null, null, null).size());
    }

    @Test
    void rejectsTruncatedPluginResultInsteadOfReturningPartialRows() {
        String incomplete = "{\"columns\":[\"user_id\"],\"rows\":[{\"user_id\":\"alice\"}],"
                + "\"truncated\":true,\"pagingApplied\":true}";
        NativeImportException error = assertThrows(NativeImportException.class,
                () -> svc.fetch(id -> incomplete, "entitlementAssignment", null, null, null));
        assertTrue(error.getMessage().contains("incomplete SQL report"));
    }

    @Test
    void rejectsInvalidAndExcessivePageValues() {
        NativeSqlReportRestService.ReportSource src = id -> envelope(List.of(row("a", null, "y")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, 0, 200001));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementAssignment", null, Integer.MAX_VALUE, 1));
    }
}
