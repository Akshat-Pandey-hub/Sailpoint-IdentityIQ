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
 * Contract for SQL-report Query 4 (Entitlement Certification Status) exposed through the shared
 * {@link NativeSqlReportRestService} at {@code /kfagent/entitlement-certification-status}. Verifies: the
 * exact report id {@code entitlementCertificationStatus}; the exact 14-column <b>outer-SELECT</b> contract
 * and order (CTE-internal {@code rn}/{@code certification_id} are NOT projected); row→JSON mapping; SQL
 * NULL preserved; the latest-certification values and {@code never_certified} flag verbatim; exact scalar
 * filtering with AND; HTTP-400-worthy {@link IllegalArgumentException} for unknown filters / unregistered
 * reports; filtering before paging; default/explicit/invalid paging; empty {@code []}; and no regression
 * to Query 1/2/3. {@link #COLUMNS} mirrors the final outer SELECT of entitlementCertificationStatus.sql.
 */
class NativeEntitlementCertificationStatusReportRestServiceTest {

    /** The 14 Query 4 OUTER-SELECT columns, in exact order (not the CTE's internal fields). */
    private static final List<String> COLUMNS = List.of(
            "user_id", "user_name", "manager_user_id", "manager_name", "application_name", "account_name",
            "entitlement_attribute", "entitlement_value", "entitlement_assigned_date",
            "last_certification_name", "last_certified_date", "certifier", "certification_decision",
            "never_certified");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NativeSqlReportRestService svc = new NativeSqlReportRestService(Set.of(
            "entitlementAssignment", "workgroupMembers", "entitlementCertification",
            "entitlementCertificationStatus"));

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

    /** One row keyed by the 14 outer columns; nulls model a never-certified entitlement (LEFT JOIN). */
    private static Map<String, String> row(String userId, String app, String neverCertified,
                                           String lastCertName, String certifier, String decision) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("user_id", userId);
        r.put("user_name", userId + " Name");
        r.put("manager_user_id", "mgr-" + userId);
        r.put("manager_name", "Manager " + userId);
        r.put("application_name", app);
        r.put("account_name", userId + "-acct");
        r.put("entitlement_attribute", "memberOf");
        r.put("entitlement_value", "Admins");
        r.put("entitlement_assigned_date", "2026-01-01");
        r.put("last_certification_name", lastCertName); // null when never certified
        r.put("last_certified_date", lastCertName == null ? null : "2026-02-01");
        r.put("certifier", certifier);                  // null when never certified
        r.put("certification_decision", decision);      // null when never certified
        r.put("never_certified", neverCertified);
        return r;
    }

    private static String envelope(List<Map<String, String>> rows) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("report", "entitlementCertificationStatus");
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
                row("alice", "AD", "NO", "Q1 Cert", "cert-admin", "Approved"),
                row("bob", "AD", "YES", null, null, null),          // never certified -> cert fields null
                row("carol", "LDAP", "NO", "Q2 Cert", "cert-mgr", "Revoked"));
    }

    @Test
    void requestsExactReportIdAndRejectsUnregisteredReports() {
        CapturingSource src = new CapturingSource(envelope(sample()));
        List<Map<String, Object>> rows = svc.fetch(src, "entitlementCertificationStatus", null, null, null);
        assertEquals(3, rows.size());
        assertEquals("entitlementCertificationStatus", src.requestedId, "plugin asked for the exact report id");

        assertTrue(svc.registeredReports().contains("entitlementCertificationStatus"));
        assertTrue(svc.registeredReports().contains("entitlementAssignment"), "Query 1 still registered");
        assertTrue(svc.registeredReports().contains("workgroupMembers"), "Query 2 still registered");
        assertTrue(svc.registeredReports().contains("entitlementCertification"), "Query 3 still registered");

        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), "arbitrary", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(new CapturingSource("{}"), null, null, null, null));
    }

    @Test
    void exactOuterColumnContractOrderMappingNullsAndCteInternalExcluded() {
        List<Map<String, Object>> rows =
                svc.fetch(new CapturingSource(envelope(sample())), "entitlementCertificationStatus", null, null, null);
        assertEquals(3, rows.size());

        Map<String, Object> certified = rows.get(0);
        assertEquals(14, certified.size(), "exactly the 14 outer-SELECT columns");
        assertEquals(COLUMNS, new ArrayList<>(certified.keySet()), "outer SELECT order");
        assertEquals("alice", certified.get("user_id"));
        assertEquals("mgr-alice", certified.get("manager_user_id"));
        assertEquals("Q1 Cert", certified.get("last_certification_name"));
        assertEquals("cert-admin", certified.get("certifier"));
        assertEquals("Approved", certified.get("certification_decision"));
        assertEquals("NO", certified.get("never_certified"));

        // CTE-internal fields are never surfaced as report columns.
        assertFalse(certified.containsKey("rn"), "CTE row-number must not be exposed");
        assertFalse(certified.containsKey("certification_id"), "CTE internal id must not be exposed");

        // Never-certified row: cert fields are SQL NULL and preserved.
        Map<String, Object> never = rows.get(1);
        assertEquals("YES", never.get("never_certified"));
        assertTrue(never.containsKey("last_certification_name"));
        assertNull(never.get("last_certification_name"), "SQL NULL preserved");
        assertNull(never.get("last_certified_date"));
        assertNull(never.get("certifier"));
        assertNull(never.get("certification_decision"));

        // No KeyForge persistence metadata.
        assertFalse(certified.containsKey("id"));
        assertFalse(certified.containsKey("record_hash"));
        assertFalse(certified.containsKey("extraction_run_id"));
        assertFalse(certified.containsKey("extracted_at"));
    }

    @Test
    void scalarFiltersAndAndCombine() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(2, svc.fetch(src, "entitlementCertificationStatus", Map.of("never_certified", "NO"), null, null).size());
        assertEquals(1, svc.fetch(src, "entitlementCertificationStatus", Map.of("certification_decision", "Revoked"), null, null).size());
        // AND
        assertEquals(1, svc.fetch(src, "entitlementCertificationStatus",
                Map.of("application_name", "AD", "never_certified", "NO"), null, null).size());
        assertEquals(0, svc.fetch(src, "entitlementCertificationStatus",
                Map.of("application_name", "LDAP", "never_certified", "YES"), null, null).size());
        // case-sensitive
        assertEquals(0, svc.fetch(src, "entitlementCertificationStatus", Map.of("certifier", "CERT-ADMIN"), null, null).size());
    }

    @Test
    void unknownAndCteInternalFilterNamesRejected() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertificationStatus", Map.of("nope", "x"), null, null));
        // CTE-internal names are not runtime columns -> rejected like any unknown filter
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertificationStatus", Map.of("rn", "1"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertificationStatus", Map.of("certification_id", "x"), null, null));
    }

    @Test
    void filteringHappensBeforePaging() {
        String body = envelope(List.of(
                row("u1", "AD", "NO", "C", "x", "Approved"),
                row("u2", "AD", "YES", null, null, null),
                row("u3", "AD", "NO", "C", "x", "Approved"),
                row("u4", "AD", "NO", "C", "x", "Approved")));
        NativeSqlReportRestService.ReportSource src = id -> body;
        // filter NO -> u1,u3,u4 ; window start=1,limit=1 -> u3
        List<Map<String, Object>> win =
                svc.fetch(src, "entitlementCertificationStatus", Map.of("never_certified", "NO"), 1, 1);
        assertEquals(1, win.size());
        assertEquals("u3", win.get(0).get("user_id"));
    }

    @Test
    void defaultExplicitAndInvalidPaging() {
        String body = envelope(sample());
        NativeSqlReportRestService.ReportSource src = id -> body;

        assertEquals(3, svc.fetch(src, "entitlementCertificationStatus", null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(src, "entitlementCertificationStatus", null, 1, 5);
        assertEquals(2, win.size());
        assertEquals("bob", win.get(0).get("user_id"));
        assertEquals(1, svc.fetch(src, "entitlementCertificationStatus", null, 0, 1).size());
        assertEquals(3, svc.fetch(src, "entitlementCertificationStatus", null, -5, null).size());
        assertEquals(3, svc.fetch(src, "entitlementCertificationStatus", null, null, -1).size());
        assertEquals(0, svc.fetch(src, "entitlementCertificationStatus", null, 99, 5).size());
    }

    @Test
    void emptyResultReturnsEmptyArrayButStillValidatesFilterColumns() {
        String body = envelope(List.of());
        NativeSqlReportRestService.ReportSource src = id -> body;
        assertEquals(List.of(), svc.fetch(src, "entitlementCertificationStatus", null, null, null));
        assertEquals(List.of(), svc.fetch(src, "entitlementCertificationStatus", Map.of("user_id", "x"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> svc.fetch(src, "entitlementCertificationStatus", Map.of("bogus", "x"), null, null));
    }

    @Test
    void query1Query2Query3StillWorkThroughSameService() {
        String q1 = "{\"report\":\"entitlementAssignment\",\"columns\":[\"user_id\"],"
                + "\"rows\":[{\"user_id\":\"alice\"}],\"returned\":1,\"truncated\":false}";
        String q2 = "{\"report\":\"workgroupMembers\",\"columns\":[\"user_status\"],"
                + "\"rows\":[{\"user_status\":\"Active\"}],\"returned\":1,\"truncated\":false}";
        String q3 = "{\"report\":\"entitlementCertification\",\"columns\":[\"never_certified\"],"
                + "\"rows\":[{\"never_certified\":\"YES\"}],\"returned\":1,\"truncated\":false}";
        assertEquals("alice", svc.fetch(id -> q1, "entitlementAssignment", null, null, null).get(0).get("user_id"));
        assertEquals("Active", svc.fetch(id -> q2, "workgroupMembers", null, null, null).get(0).get("user_status"));
        assertEquals("YES", svc.fetch(id -> q3, "entitlementCertification", null, null, null).get(0).get("never_certified"));
    }

    @Test
    void restPathHasNoPostgresDependency() {
        NativeSqlReportRestService.ReportSource pureLambda = id -> envelope(sample());
        assertEquals(3, svc.fetch(pureLambda, "entitlementCertificationStatus", null, null, null).size());
    }
}
