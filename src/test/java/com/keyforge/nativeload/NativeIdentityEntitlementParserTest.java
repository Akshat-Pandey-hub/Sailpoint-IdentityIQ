package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing + fail-loud behaviour for the native IdentityEntitlement payload. */
class NativeIdentityEntitlementParserTest {

    private final NativeIdentityEntitlementParser parser = new NativeIdentityEntitlementParser();

    @Test
    void parsesRowsAndProvenanceAndSourceCount() {
        String json = "{\"sourceCount\":2,\"rows\":["
                + "{\"sourceId\":\"s1\",\"identityName\":\"alice\",\"attributeName\":\"memberOf\","
                + "\"attributeValue\":\"admins\",\"grantedByRole\":true,\"source\":\"Rule\","
                + "\"valueList\":[\"admins\",\"users\"]},"
                + "{\"sourceId\":\"s2\",\"identityName\":\"bob\"}]}";
        List<NativeIdentityEntitlementRecord> rows = parser.parse(json);
        assertEquals(2, rows.size());
        assertEquals(2, parser.sourceCount(json));
        assertEquals("alice", rows.get(0).identityName);
        assertEquals(Boolean.TRUE, rows.get(0).grantedByRole);
        assertTrue(rows.get(0).valueListJson.contains("admins"));
    }

    @Test
    void genuineEmptyRowsReturnsEmpty() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
    }

    @Test
    void missingRowsArrayFailsLoudly() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"IdentityEntitlement\"}"));
        assertThrows(NativeImportException.class, () -> parser.parse("not json"));
    }
}
