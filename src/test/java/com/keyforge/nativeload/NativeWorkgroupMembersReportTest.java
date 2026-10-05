package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Query 2 (workgroup-members) contract over the shared SQL-report plumbing: the seven business aliases
 * are carried in order, SQL NULL is preserved, and the CASE-driven {@code user_status} values
 * ("No Members" / "Disabled" / "Active") arrive verbatim. Mirrors the Query 1 parser-test discipline;
 * the result-set is projected exactly as the plugin's {@code getColumnLabel}/{@code getString} emit it.
 */
class NativeWorkgroupMembersReportTest {

    private final NativeSqlReportParser parser = new NativeSqlReportParser();

    private static final String ENVELOPE = "{\"report\":\"workgroupMembers\","
            + "\"columns\":[\"workgroup_name\",\"user_name\",\"display_name\",\"first_name\","
            + "\"last_name\",\"email\",\"user_status\"],"
            + "\"rows\":["
            // Active member, with a value in every column
            + "{\"workgroup_name\":\"IT Admins\",\"user_name\":\"ada\",\"display_name\":\"Ada Lovelace\","
            + "\"first_name\":\"Ada\",\"last_name\":\"Lovelace\",\"email\":\"ada@corp.com\",\"user_status\":\"Active\"},"
            // Disabled member, with a NULL email (SQL NULL must stay null)
            + "{\"workgroup_name\":\"IT Admins\",\"user_name\":\"bob\",\"display_name\":\"Bob Stone\","
            + "\"first_name\":\"Bob\",\"last_name\":\"Stone\",\"email\":null,\"user_status\":\"Disabled\"},"
            // Empty workgroup: the LEFT JOIN yields NULL user columns and user_status 'No Members'
            + "{\"workgroup_name\":\"Empty WG\",\"user_name\":null,\"display_name\":null,"
            + "\"first_name\":null,\"last_name\":null,\"email\":null,\"user_status\":\"No Members\"}"
            + "],\"returned\":3,\"truncated\":false}";

    @Test
    void carriesSevenAliasesInOrder() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);
        assertEquals(
                List.of("workgroup_name", "user_name", "display_name", "first_name", "last_name", "email", "user_status"),
                r.columns);
        assertEquals(3, r.rows.size());
    }

    @Test
    void preservesValuesAndNulls() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);

        Map<String, String> active = r.rows.get(0);
        assertEquals("IT Admins", active.get("workgroup_name"));
        assertEquals("Ada Lovelace", active.get("display_name"));
        assertEquals("ada@corp.com", active.get("email"));

        Map<String, String> disabled = r.rows.get(1);
        assertNull(disabled.get("email"), "SQL NULL email must stay null, not the string 'null'");

        Map<String, String> noMembers = r.rows.get(2);
        assertNull(noMembers.get("user_name"), "empty-workgroup member columns must be null");
        assertNull(noMembers.get("display_name"));
    }

    @Test
    void carriesAllThreeUserStatusOutcomes() {
        NativeSqlReportParser.Result r = parser.parse(ENVELOPE);
        assertEquals("Active", r.rows.get(0).get("user_status"));
        assertEquals("Disabled", r.rows.get(1).get("user_status"));
        assertEquals("No Members", r.rows.get(2).get("user_status"));
    }

    @Test
    void errorEnvelopeStillFailsLoud() {
        String err = "{\"report\":\"workgroupMembers\",\"status\":500,"
                + "\"error\":{\"type\":\"org.postgresql.util.PSQLException\",\"message\":\"boom\"}}";
        assertThrows(NativeImportException.class, () -> parser.parse(err));
    }
}
