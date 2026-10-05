package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser contract for the SQL-report envelope: column order, verbatim values, NULL fidelity, errors. */
class NativeSqlReportParserTest {

    private final NativeSqlReportParser parser = new NativeSqlReportParser();

    @Test
    void parsesColumnsRowsAndPreservesNull() {
        String json = "{\"report\":\"entitlementAssignment\","
                + "\"columns\":[\"identity_id\",\"assignment_type\",\"role_requestable\"],"
                + "\"rows\":["
                + "{\"identity_id\":\"7f01\",\"assignment_type\":\"TARGET ASSIGNED / AGGREGATED\",\"role_requestable\":null},"
                + "{\"identity_id\":\"7f02\",\"assignment_type\":\"DIRECT ENTITLEMENT REQUEST\",\"role_requestable\":\"true\"}"
                + "],\"returned\":2,\"truncated\":false}";

        NativeSqlReportParser.Result r = parser.parse(json);

        assertEquals(List.of("identity_id", "assignment_type", "role_requestable"), r.columns);
        assertEquals(2, r.rows.size());
        assertFalse(r.truncated);

        Map<String, String> row0 = r.rows.get(0);
        assertEquals("7f01", row0.get("identity_id"));
        assertEquals("TARGET ASSIGNED / AGGREGATED", row0.get("assignment_type"));
        assertNull(row0.get("role_requestable"), "SQL NULL must stay null, not the string 'null'");
        assertEquals("true", r.rows.get(1).get("role_requestable"));
    }

    @Test
    void truncatedFlagIsCarried() {
        String json = "{\"columns\":[\"a\"],\"rows\":[{\"a\":\"x\"}],\"truncated\":true}";
        assertTrue(parser.parse(json).truncated);
    }

    @Test
    void errorEnvelopeThrows() {
        String json = "{\"report\":\"entitlementAssignment\",\"status\":500,"
                + "\"error\":{\"type\":\"java.sql.SQLException\",\"message\":\"bad column\"}}";
        assertThrows(NativeImportException.class, () -> parser.parse(json));
    }

    @Test
    void nonJsonThrows() {
        assertThrows(NativeImportException.class, () -> parser.parse("<html>login</html>"));
    }

    @Test
    void missingRowsArrayThrows() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"columns\":[\"a\"]}"));
    }
}
