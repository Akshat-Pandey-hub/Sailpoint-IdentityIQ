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
 * Contract for SQL-report Query 3 (Entitlement Certification) exposed through the shared
 * {@link NativeSqlReportRestService} at {@code /kfagent/entitlement-certification}. The service is fed a
 * fake report source returning a canned plugin envelope (no live IIQ, no DB), and verifies: the exact
 * report id {@code entitlementCertification}; the exact nine-column contract and SELECT order; row→JSON
 * mapping; SQL NULL preserved as JSON null; the {@code never_certified} 'YES'/'NO' flag verbatim; exact
 * scalar filtering with AND; HTTP-400-worthy {@link IllegalArgumentException} for unknown filters /
 * unregistered reports; filtering before paging; default/explicit/invalid paging; empty {@code []}; and
 * that Query 1 and Query 2 stay registered (no regression). {@link #COLUMNS} mirrors
 * entitlementCertification.sql's SELECT order.
 */
class NativeEntitlementCertificationReportRestServiceTest {

    /** The 9 Query 3 columns, in the exact order of entitlementCertification.sql's SELECT. */
    private static final List<String> COLUMNS = List.of(
            "user_id", "user_name", "application_name", "account_name", "entitlement_attribute",
            "entitlement_value", "entitlement_assigned_date", "entitlement_modified_date", "never_certified");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Mirrors the real server registration so "no Query 1/2 regression" is meaningful here.
    private final NativeSqlReportRestService svc = new NativeSqlReportRestService(
            Set.of("entitlementAssignment", "workgroupMembers", "entitlementCertification"));

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

    /** One row keyed by the 9 columns; a null value models the report's SQL NULL (e.g. a never-modified date). */
    private static Map<String, String> row(String userId, String app, String attr, String value,
                                           String assigned, String modified, String neverCertified) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("user_id", userId);
        r.put("user_name", userId + " Name");
        r.put("application_name", app);
        r.put("account_name", userId + "-acct");
        r.put("entitlement_attribute", attr);
        r.put("entitlement_value", value);
        r.put("entitlement_assigned_date", assigned);
        r.put("entitlement_modified_date", modified);
        r.put("never_certified", neverCertified);
        return r;
    }

    private static String envelope(List<Map<String, String>> rows) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("report", "entitlementCertification");
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

    private static List<Map<String, String>> sample() {
        return List.of(
                row("alice", "AD", "memberOf", "Admins", "2026-01-01", "2026-02-01", "NO"),
                row("bob", "AD", "memberOf", "Users", "2026-01-05", null, "YES"),
                row("carol", "LDAP", "group", "Dev", "2026-03-01", "2026-03-02", "YES"));
    }

    @Test
    void requestsExactReportIdAndRejectsUnregisteredReports() {
        CapturingSource src = new CapturingSource(envelope(sample()));
        List<Map<String, Object>> rows = svc.fetch(src, "entitlementCertification", null, null, null);
        assertEquals(3, rows.size());
        assertEquals("entitlementCertification", src.requestedId, "plugin asked for the exact server report id");

        assertTrue(svc.registeredReports().contains("entitlementCertification"));
        assertTrue(svc.registeredReports().contains("entitlementAssignment"), "Query 1 still registered");
        assertTrue(svc.registeredReports().contains("workgroupMembers"), "Query 2 still registered");

        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "arbitrary", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "entitlementCertificationStatus", null, null, null)); // Query 4 NOT registered
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), null, null, null, null));
    }

    @Test
    void exactNineColumnContractOrderMappingNullAndFlagPreserved() {
        List<Map<String, Object>> rows =
                svc.fetch(new CapturingSource(envelope(sample())), "entitlementCertification", null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> first = rows.get(0);
        assertEquals(9, first.size(), "exactly the 9 report columns");
        assertEquals(COLUMNS, new ArrayList<>(first.keySet()), "columns in the SQL SELECT order");
        assertEquals("alice", first.get("user_id"));
        assertEquals("Admins", first.get("entitlement_value"));
        assertEquals("2026-02-01", first.get("entitlement_modified_date"));
        assertEquals("NO", first.get("never_certified"));

        Map<String, Object> second = rows.get(1);
        assertTrue(second.containsKey("entitlement_modified_date"), "null column still present");
        assertNull(second.get("entitlement_modified_date"), "SQL NULL preserved as JSON null");
        assertEquals("YES", second.get("never_certified"));

        // No KeyForge persistence metadata leaks in.
        assertFalse(first.containsKey("id"));
        assertFalse(first.containsKey("record_hash"));
        assertFalse(first.containsKey("extraction_run_id"));
        assertFalse(first.containsKey("extracted_at"));
        assertFalse(first.containsKey("src_natural_key"));
    }

    @Test
    void scalarFiltersAndAndCombine() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(2, svc.fetch(src, "entitlementCertification", Map.of("never_certified", "YES"), null, null).size());
        assertEquals(1, svc.fetch(src, "entitlementCertification", Map.of("user_id", "bob"), null, null).size());
        // AND across two columns
        assertEquals(1, svc.fetch(src, "entitlementCertification",
                Map.of("application_name", "AD", "never_certified", "YES"), null, null).size());
        assertEquals(0, svc.fetch(src, "entitlementCertification",
                Map.of("application_name", "LDAP", "never_certified", "NO"), null, null).size());
        // case-sensitive
        assertEquals(0, svc.fetch(src, "entitlementCertification", Map.of("never_certified", "yes"), null, null).size());
    }

    @Test
    void unknownAndCrossReportFilterNamesRejected() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertification", Map.of("nope", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertification", Map.of("record_hash", "x"), null, null));
        // a Query 1/2 column is not a Query 3 runtime column -> rejected
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertification", Map.of("workgroup_name", "x"), null, null));
    }

    @Test
    void filteringHappensBeforePaging() {
        String body = envelope(List.of(
                row("u1", "AD", "a", "v", "d", "m", "YES"),
                row("u2", "AD", "a", "v", "d", "m", "NO"),
                row("u3", "AD", "a", "v", "d", "m", "YES"),
                row("u4", "AD", "a", "v", "d", "m", "YES")));
        NativeSqlReportRestService.ReportSource src = id -> body;
        // filter YES -> u1,u3,u4 ; window start=1,limit=1 -> u3
        List<Map<String, Object>> win =
                svc.fetch(src, "entitlementCertification", Map.of("never_certified", "YES"), 1, 1);
        assertEquals(1, win.size());
        assertEquals("u3", win.get(0).get("user_id"));
    }

    @Test
    void defaultExplicitAndInvalidPaging() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(3, svc.fetch(src, "entitlementCertification", null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(src, "entitlementCertification", null, 1, 5);
        assertEquals(2, win.size());
        assertEquals("bob", win.get(0).get("user_id"));
        assertEquals(1, svc.fetch(src, "entitlementCertification", null, 0, 1).size());
        assertEquals(3, svc.fetch(src, "entitlementCertification", null, -5, null).size()); // start<0 -> 0
        assertEquals(3, svc.fetch(src, "entitlementCertification", null, null, -1).size()); // limit<0 -> all
        assertEquals(0, svc.fetch(src, "entitlementCertification", null, 99, 5).size());    // past end -> []
    }

    @Test
    void emptyResultReturnsEmptyArrayButStillValidatesFilterColumns() {
        String body = envelope(List.of());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertEquals(List.of(), svc.fetch(src, "entitlementCertification", null, null, null));
        assertEquals(List.of(), svc.fetch(src, "entitlementCertification", Map.of("user_id", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertification", Map.of("bogus", "x"), null, null));
    }

    @Test
    void query1AndQuery2StillWorkThroughSameService() {
        String q1 = "{\"report\":\"entitlementAssignment\",\"columns\":[\"user_id\",\"role_name\"],"
                + "\"rows\":[{\"user_id\":\"alice\",\"role_name\":\"Admin\"}],\"returned\":1,\"truncated\":false}";
        String q2 = "{\"report\":\"workgroupMembers\",\"columns\":[\"workgroup_name\",\"user_status\"],"
                + "\"rows\":[{\"workgroup_name\":\"IT\",\"user_status\":\"Active\"}],\"returned\":1,\"truncated\":false}";
        assertEquals("alice",
                svc.fetch(id -> q1, "entitlementAssignment", null, null, null).get(0).get("user_id"));
        assertEquals("Active",
                svc.fetch(id -> q2, "workgroupMembers", null, null, null).get(0).get("user_status"));
    }

    @Test
    void restPathHasNoPostgresDependency() {
        NativeSqlReportRestService.ReportSource pureLambda = id -> envelope(sample());
        assertEquals(3, svc.fetch(pureLambda, "entitlementCertification", null, null, null).size());
    }
}
