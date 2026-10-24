package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
        assertTrue(sql("entitlementCertificationStatus.sql")
                        .lastIndexOf("WHERE\n    ci.exception_application IS NOT NULL")
                        < sql("entitlementCertificationStatus.sql").lastIndexOf("\nWHERE\n(") ,
                "the certification-history CTE predicate remains in place and the new filter is on the outer query");
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
