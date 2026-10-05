package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Query 3 (entitlement / never-certified) contract over the shared SQL-report plumbing: the nine business
 * aliases are carried in order, SQL NULL is preserved, the {@code to_timestamp(...)} date values arrive
 * verbatim as the plugin's {@code getString} renders them, and the CASE-driven {@code never_certified}
 * flag ("NO" when a matching certification item EXISTS, else "YES") is projected exactly. The EXISTS /
 * target_id-or-target_name / native_identity-NULL-fallback matching is SQL executed inside IIQ, so it is
 * exercised by the live run; this test pins the envelope → rows projection, mirroring Q1/Q2 discipline.
 */
class NativeEntitlementCertificationReportTest {

    private final NativeSqlReportParser parser = new NativeSqlReportParser();

    private static final String ENVELOPE = "{\"report\":\"entitlementCertification\","
            + "\"columns\":[\"user_id\",\"user_name\",\"application_name\",\"account_name\","
            + "\"entitlement_attribute\",\"entitlement_value\",\"entitlement_assigned_date\","
            + "\"entitlement_modified_date\",\"never_certified\"],"
            + "\"rows\":["
            // Certified entitlement: a matching cert item EXISTS -> never_certified = 'NO'
            + "{\"user_id\":\"ada\",\"user_name\":\"Ada Lovelace\",\"application_name\":\"Active Directory\","
            + "\"account_name\":\"CN=ada\",\"entitlement_attribute\":\"memberOf\",\"entitlement_value\":\"Admins\","
            + "\"entitlement_assigned_date\":\"2026-01-02 03:04:05.678+00\","
            + "\"entitlement_modified_date\":\"2026-02-03 04:05:06.789+00\",\"never_certified\":\"NO\"},"
            // Never-certified entitlement, with a NULL account_name and NULL dates (SQL NULL must stay null)
            + "{\"user_id\":\"bob\",\"user_name\":\"Bob Stone\",\"application_name\":\"Workday\","
            + "\"account_name\":null,\"entitlement_attribute\":\"role\",\"entitlement_value\":\"Viewer\","
            + "\"entitlement_assigned_date\":null,\"entitlement_modified_date\":null,\"never_certified\":\"YES\"}"
            + "],\"returned\":2,\"truncated\":false}";

    @Test
    void carriesNineAliasesInOrder() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);
        assertEquals(
                List.of("user_id", "user_name", "application_name", "account_name", "entitlement_attribute",
                        "entitlement_value", "entitlement_assigned_date", "entitlement_modified_date",
                        "never_certified"),
                r.columns);
        assertEquals(2, r.rows.size());
    }

    @Test
    void preservesValuesDatesAndNulls() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);

        Map<String, String> certified = r.rows.get(0);
        assertEquals("ada", certified.get("user_id"));
        assertEquals("CN=ada", certified.get("account_name"));
        assertEquals("2026-01-02 03:04:05.678+00", certified.get("entitlement_assigned_date"));
        assertEquals("2026-02-03 04:05:06.789+00", certified.get("entitlement_modified_date"));

        Map<String, String> never = r.rows.get(1);
        assertNull(never.get("account_name"), "SQL NULL account_name must stay null");
        assertNull(never.get("entitlement_assigned_date"), "SQL NULL date must stay null");
        assertNull(never.get("entitlement_modified_date"));
    }

    @Test
    void carriesBothNeverCertifiedOutcomes() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);
        assertEquals("NO", r.rows.get(0).get("never_certified"), "matching cert item EXISTS -> 'NO'");
        assertEquals("YES", r.rows.get(1).get("never_certified"), "no matching cert item -> 'YES'");
    }

    @Test
    void errorEnvelopeStillFailsLoud() {
        String err = "{\"report\":\"entitlementCertification\",\"status\":500,"
                + "\"error\":{\"type\":\"org.postgresql.util.PSQLException\",\"message\":\"boom\"}}";
        assertThrows(NativeImportException.class, () -> parser.parse(err));
    }
}
