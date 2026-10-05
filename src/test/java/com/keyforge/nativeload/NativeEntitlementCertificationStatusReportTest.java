package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Query 4 (entitlement + latest-certification status) contract over the shared SQL-report plumbing: the
 * fourteen business aliases are carried in order, SQL NULL is preserved across every nullable column, the
 * {@code to_timestamp(...)} date values arrive verbatim, and the three certification states are projected
 * distinctly: never-certified (YES; all cert fields null), certified-without-decision (NO; certification
 * name present but decision/date/certifier null), and certified-with-decision (NO; all populated). The
 * CTE / ROW_NUMBER() latest-record selection and the target/exception/native_identity matching are SQL
 * executed inside IIQ, exercised by the live run; this test pins the envelope → rows projection, mirroring
 * the Q1–Q3 discipline.
 */
class NativeEntitlementCertificationStatusReportTest {

    private final NativeSqlReportParser parser = new NativeSqlReportParser();

    private static final String ENVELOPE = "{\"report\":\"entitlementCertificationStatus\","
            + "\"columns\":[\"user_id\",\"user_name\",\"manager_user_id\",\"manager_name\",\"application_name\","
            + "\"account_name\",\"entitlement_attribute\",\"entitlement_value\",\"entitlement_assigned_date\","
            + "\"last_certification_name\",\"last_certified_date\",\"certifier\",\"certification_decision\","
            + "\"never_certified\"],"
            + "\"rows\":["
            // State 1: certified WITH a decision -> never_certified='NO', all cert fields populated
            + "{\"user_id\":\"alex.cook\",\"user_name\":\"Alexander Cook\",\"manager_user_id\":\"dana.lee\","
            + "\"manager_name\":\"Dana Lee\",\"application_name\":\"EntraTarget\",\"account_name\":\"881d4680\","
            + "\"entitlement_attribute\":\"groups\",\"entitlement_value\":\"001ec823\","
            + "\"entitlement_assigned_date\":\"2026-09-01 10:00:00+00\",\"last_certification_name\":\"Q3 Access Review\","
            + "\"last_certified_date\":\"2026-09-20 12:00:00+00\",\"certifier\":\"dana.lee\","
            + "\"certification_decision\":\"Approved\",\"never_certified\":\"NO\"},"
            // State 2: certified WITHOUT a decision -> never_certified='NO', decision/date/certifier null
            + "{\"user_id\":\"alex.evans\",\"user_name\":\"Alexander Evans\",\"manager_user_id\":null,"
            + "\"manager_name\":null,\"application_name\":\"EntraTarget\",\"account_name\":null,"
            + "\"entitlement_attribute\":\"groups\",\"entitlement_value\":\"02890fbb\","
            + "\"entitlement_assigned_date\":\"2026-09-02 11:00:00+00\",\"last_certification_name\":\"Q3 Access Review\","
            + "\"last_certified_date\":null,\"certifier\":null,\"certification_decision\":null,\"never_certified\":\"NO\"},"
            // State 3: never certified -> never_certified='YES', all cert fields null
            + "{\"user_id\":\"bob.stone\",\"user_name\":\"Bob Stone\",\"manager_user_id\":\"dana.lee\","
            + "\"manager_name\":\"Dana Lee\",\"application_name\":\"Workday\",\"account_name\":\"bob\","
            + "\"entitlement_attribute\":\"role\",\"entitlement_value\":\"Viewer\","
            + "\"entitlement_assigned_date\":\"2026-08-15 09:00:00+00\",\"last_certification_name\":null,"
            + "\"last_certified_date\":null,\"certifier\":null,\"certification_decision\":null,\"never_certified\":\"YES\"}"
            + "],\"returned\":3,\"truncated\":false}";

    @Test
    void carriesFourteenAliasesInOrder() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);
        assertEquals(
                List.of("user_id", "user_name", "manager_user_id", "manager_name", "application_name",
                        "account_name", "entitlement_attribute", "entitlement_value", "entitlement_assigned_date",
                        "last_certification_name", "last_certified_date", "certifier", "certification_decision",
                        "never_certified"),
                r.columns);
        assertEquals(3, r.rows.size());
    }

    @Test
    void certifiedWithDecisionPopulatesAllFields() {
        Map<String, String> row = parser.parse(ENVELOPE).rows.get(0);
        assertEquals("NO", row.get("never_certified"));
        assertEquals("Q3 Access Review", row.get("last_certification_name"));
        assertEquals("2026-09-20 12:00:00+00", row.get("last_certified_date"));
        assertEquals("dana.lee", row.get("certifier"));
        assertEquals("Approved", row.get("certification_decision"));
        assertEquals("2026-09-01 10:00:00+00", row.get("entitlement_assigned_date"));
        assertEquals("Dana Lee", row.get("manager_name"));
    }

    @Test
    void certifiedWithoutDecisionKeepsDecisionFieldsNull() {
        Map<String, String> row = parser.parse(ENVELOPE).rows.get(1);
        assertEquals("NO", row.get("never_certified"), "a certification exists -> NO");
        assertEquals("Q3 Access Review", row.get("last_certification_name"), "certification name is present");
        assertNull(row.get("last_certified_date"), "no decision -> last_certified_date stays null");
        assertNull(row.get("certifier"), "no decision -> certifier stays null");
        assertNull(row.get("certification_decision"), "no decision -> decision stays null");
        assertNull(row.get("manager_user_id"), "null manager must stay null");
        assertNull(row.get("account_name"), "null native_identity must stay null");
    }

    @Test
    void neverCertifiedHasAllCertFieldsNull() {
        Map<String, String> row = parser.parse(ENVELOPE).rows.get(2);
        assertEquals("YES", row.get("never_certified"));
        assertNull(row.get("last_certification_name"));
        assertNull(row.get("last_certified_date"));
        assertNull(row.get("certifier"));
        assertNull(row.get("certification_decision"));
    }

    @Test
    void errorEnvelopeStillFailsLoud() {
        String err = "{\"report\":\"entitlementCertificationStatus\",\"status\":500,"
                + "\"error\":{\"type\":\"org.postgresql.util.PSQLException\",\"message\":\"boom\"}}";
        assertThrows(NativeImportException.class, () -> parser.parse(err));
    }
}
