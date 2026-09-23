package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing + fail-loud behaviour for the native account-entitlement payload. */
class NativeAccountEntitlementParserTest {

    private final NativeAccountEntitlementParser parser = new NativeAccountEntitlementParser();

    @Test
    void parsesEdgesSourceCountAndReturnedLinks() {
        String json = "{\"sourceCount\":7,\"returnedLinks\":3,\"rows\":["
                + "{\"linkId\":\"l1\",\"attributeName\":\"memberOf\",\"attributeValue\":\"g1\",\"identityName\":\"alice\"},"
                + "{\"linkId\":\"l1\",\"attributeName\":\"memberOf\",\"attributeValue\":\"g2\"}]}";
        List<NativeAccountEntitlementRecord> rows = parser.parse(json);
        assertEquals(2, rows.size());
        assertEquals(7, parser.sourceCount(json));
        assertEquals(3, parser.returnedLinks(json));
        assertEquals("g1", rows.get(0).attributeValue);
    }

    @Test
    void emptyEdgesButLinksPresentReturnsEmptyList() {
        // A Link page with no entitlements: rows empty, returnedLinks positive.
        List<NativeAccountEntitlementRecord> rows = parser.parse("{\"sourceCount\":1,\"returnedLinks\":1,\"rows\":[]}");
        assertTrue(rows.isEmpty());
        assertEquals(1, parser.returnedLinks("{\"sourceCount\":1,\"returnedLinks\":1,\"rows\":[]}"));
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
    }

    @Test
    void missingRowsArrayFailsLoudly() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"AccountEntitlement\"}"));
    }
}
