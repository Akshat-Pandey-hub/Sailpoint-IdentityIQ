package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the existing SQL report order and records what the query text proves about row grain. */
class NativeSqlReportSqlContractTest {

    @Test
    void allFourReportsKeepTheirExistingOrderByClauses() throws Exception {
        String assignment = sql("entitlementAssignment.sql");
        assertTrue(assignment.contains("SELECT DISTINCT"));
        assertTrue(assignment.contains("ORDER BY\n    i.name,\n    app.name,\n    ie.value,\n    b.name,\n    ir.created DESC,\n    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,"));
        assertTrue(assignment.contains("21, 22, 23, 24, 25, 26, 27, 28, 29"),
                "DISTINCT output tuples are ordered by all selected columns to make ties reproducible");

        String workgroups = sql("workgroupMembers.sql");
        assertTrue(workgroups.contains("ORDER BY\n    wg.name,\n    u.inactive DESC,\n    u.display_name,\n    wg.id,\n    u.id,\n    iw.idx"));
        assertFalse(workgroups.contains("SELECT DISTINCT"),
                "the output grain is the left-joined membership rows, not unique projected profiles");

        String certification = sql("entitlementCertification.sql");
        assertTrue(certification.contains("ORDER BY\n    never_certified DESC,\n    i.name,\n    a.name,\n    ie.native_identity,\n    ie.name,\n    ie.value,\n    ie.id"));
        assertFalse(certification.contains("SELECT DISTINCT"),
                "source text does not prove that projected rows are unique");

        String status = sql("entitlementCertificationStatus.sql");
        assertTrue(status.contains("ca.decision_date DESC NULLS LAST,\n                c.created DESC"));
        assertTrue(status.contains("ORDER BY\n    never_certified DESC,\n    i.name,\n    app.name,\n    ie.native_identity,\n    ie.name,\n    ie.value,\n    ie.id,\n    ch.certification_id,\n    ch.native_identity"));
        assertTrue(status.contains("ch.native_identity = ie.native_identity\n        OR ch.native_identity IS NULL"),
                "the NULL fallback can produce more than one matching history row; uniqueness is not proven");
    }

    @Test
    void reportProjectionWidthsMatchTheirKnownRowGrainAndAssignmentOrderKey() throws Exception {
        Map<String, Integer> widths = new LinkedHashMap<>();
        widths.put("entitlementAssignment.sql", 29);
        widths.put("workgroupMembers.sql", 7);
        widths.put("entitlementCertification.sql", 9);
        widths.put("entitlementCertificationStatus.sql", 14);
        for (Map.Entry<String, Integer> report : widths.entrySet()) {
            String text = sql(report.getKey());
            int select = report.getKey().equals("entitlementAssignment.sql")
                    ? text.indexOf("SELECT DISTINCT") + "SELECT DISTINCT".length()
                    : text.lastIndexOf("SELECT\n");
            assertTrue(select >= 0, "SELECT exists in " + report.getKey());
            assertTrue(report.getValue().intValue() == selectWidth(text, select),
                    report.getKey() + " selected-column count matches its report contract");
        }
    }

    @Test
    void everyReportHasItsSqlBoundPredicateAndExpectedPlaceholderCount() throws Exception {
        Map<String, Integer> parameters = new LinkedHashMap<>();
        parameters.put("entitlementAssignment.sql", 4);
        parameters.put("workgroupMembers.sql", 2);
        parameters.put("entitlementCertification.sql", 6);
        parameters.put("entitlementCertificationStatus.sql", 10);
        for (Map.Entry<String, Integer> report : parameters.entrySet()) {
            String text = sql(report.getKey());
            assertTrue(text.contains("CAST(? AS BIGINT)"), report.getKey() + " uses BIGINT bound casts");
            long count = text.chars().filter(ch -> ch == '?').count();
            assertEquals(report.getValue().longValue(), count, report.getKey() + " placeholder count");
        }
        assertTrue(sql("entitlementAssignment.sql").contains(
                        "GREATEST(ie.created, ie.modified, i.modified, app.modified, b.modified,\n"
                                + "                p.created, p.modified,\n"
                                + "                ir.created, ir.modified, iri.created, iri.modified)"),
                "role profile timestamps participate in the incremental predicate");
        assertTrue(sql("entitlementCertificationStatus.sql")
                        .lastIndexOf("WHERE\n    ci.exception_application IS NOT NULL")
                        < sql("entitlementCertificationStatus.sql").lastIndexOf("\nWHERE\n(") ,
                "the certification-history CTE predicate remains in place and the new filter is on the outer query");
    }

    @Test
    void assignmentRequestRowsAreTargetScopedAndRoleReasonUsesTheRoleMatch() throws Exception {
        for (String resource : new String[] {
                "entitlementAssignment.sql", "entitlementAssignment.oracle.sql"}) {
            String text = sql(resource);
            int itemJoin = text.indexOf("LEFT JOIN spt_identity_request_item iri");
            int requestJoin = text.indexOf("LEFT JOIN spt_identity_request ir", itemJoin);
            assertTrue(itemJoin >= 0 && requestJoin > itemJoin, resource + " has the request-item join");
            String join = text.substring(itemJoin, requestJoin);
            assertTrue(join.contains("ON iri.native_identity = ie.native_identity")
                            && join.contains("AND EXISTS (")
                            && join.contains("FROM spt_identity_request ir_match")
                            && join.contains("ir_match.id = iri.identity_request_id")
                            && join.contains("ir_match.target_id = i.id"),
                    resource + " only attaches items whose parent request targets the entitlement identity");

            boolean oracle = resource.endsWith("oracle.sql");
            List<String> typeBranches = caseConditions(text,
                    oracle ? "\"assignment_type\"" : "assignment_type");
            List<String> reasonBranches = caseConditions(text,
                    oracle ? "\"assignment_reason\"" : "assignment_reason");
            assertEquals(5, typeBranches.size(), resource + " assignment classification branches");
            assertEquals(4, reasonBranches.size(), resource + " assignment explanation branches");
            assertEquals(typeBranches.get(0), reasonBranches.get(0),
                    resource + " requested-role reason has the exact classification predicate");
            assertEquals(typeBranches.get(1), reasonBranches.get(1),
                    resource + " no-role-request reason has the exact birthright predicate");

            String direct = typeBranches.get(2);
            assertTrue(direct.contains(oracle ? "nvl(ie.granted_by_role, 0) = 0"
                            : "ie.granted_by_role = false or ie.granted_by_role is null")
                            && direct.contains(oracle ? "ie.assigned = 1" : "ie.assigned = true")
                            && direct.contains(oracle ? "upper(ie.source) = 'lcm'"
                            : "upper(coalesce(ie.source,'')) = 'lcm'")
                            && direct.contains("ir3.target_id = i.id")
                            && direct.contains("iri3.native_identity = ie.native_identity")
                            && direct.contains("iri3.name = ie.name")
                            && direct.contains("iri3.name = ie.value")
                            && direct.contains("iri3.value = ie.value")
                            && direct.contains("iri3.operation = 'add'")
                            && direct.contains("iri3.operation = 'entitlementadd'")
                            && direct.contains("iri3.operation = 'assign'"),
                    resource + " preserves the direct-entitlement request predicates");
            String directReason = reasonBranches.get(2);
            assertTrue(directReason.contains(oracle ? "ie.assigned = 1" : "ie.assigned = true")
                            && directReason.contains(oracle ? "upper(ie.source) = 'lcm'"
                            : "upper(coalesce(ie.source,'')) = 'lcm'"),
                    resource + " retains the source-based direct LCM explanation");
            assertTrue(reasonBranches.get(3).contains(oracle
                            ? "nvl(ie.assigned, 0) = 0" : "ie.assigned = false or ie.assigned is null")
                            && reasonBranches.get(3).contains(oracle
                            ? "nvl(ie.granted_by_role, 0) = 0"
                            : "ie.granted_by_role = false or ie.granted_by_role is null"),
                    resource + " aggregated-assignment reason retains both null-safe flag checks");

            assertTrue(text.contains("SELECT DISTINCT") && text.contains("ir.id AS")
                            && text.contains("iri.operation AS"),
                    resource + " retains request-level report rows and their operation details");
        }
    }

    private static List<String> whenConditions(String caseExpression) {
        List<String> conditions = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int when = caseExpression.indexOf("WHEN", cursor);
            if (when < 0) return conditions;
            int then = caseExpression.indexOf("THEN", when);
            if (then < 0) return conditions;
            conditions.add(caseExpression.substring(when + 4, then).replaceAll("\\s+", " ").trim());
            cursor = then + 4;
        }
    }

    private static List<String> caseConditions(String sql, String alias) {
        int end = sql.indexOf("END AS " + alias);
        if (end < 0) throw new AssertionError("Missing CASE output alias " + alias);
        int start = sql.lastIndexOf("CASE", end);
        if (start < 0) throw new AssertionError("Missing CASE for output alias " + alias);
        List<String> conditions = whenConditions(sql.substring(start, end));
        List<String> normalized = new ArrayList<>(conditions.size());
        for (String condition : conditions) {
            normalized.add(condition.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim());
        }
        return normalized;
    }


    private static int selectWidth(String sql, int start) {
        int depth = 0;
        boolean quoted = false;
        int commas = 0;
        for (int i = start; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
            } else if (!quoted) {
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                else if (depth == 0 && sql.regionMatches(true, i, "FROM", 0, 4)
                        && (i == 0 || Character.isWhitespace(sql.charAt(i - 1)))
                        && (i + 4 == sql.length() || Character.isWhitespace(sql.charAt(i + 4)))) {
                    return commas + 1;
                } else if (depth == 0 && ch == ',') {
                    commas++;
                }
            }
        }
        throw new AssertionError("No top-level FROM after SELECT");
    }

    private static String sql(String name) throws Exception {
        String path = "/com/keyforge/nativeiiq/sql/" + name;
        try (InputStream in = NativeSqlReportSqlContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new AssertionError("Missing bundled SQL: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
