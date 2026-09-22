package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser must never let an IIQ error (or any non-envelope body) masquerade as an empty page —
 * that was the "0 extracted with no error" defect. A genuine empty page still returns empty cleanly.
 */
class NativeIdentityParserTest {

    private final NativeIdentityParser parser = new NativeIdentityParser();

    @Test
    void validEnvelopeParsesRows() {
        List<NativeIdentityRecord> rows = parser.parse(
                "{\"entity\":\"Identity\",\"returned\":1,\"rows\":[{\"sourceId\":\"abc\",\"name\":\"jsmith\"}]}");
        assertEquals(1, rows.size());
        assertEquals("jsmith", rows.get(0).getName());
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"Identity\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"Identity\",\"status\":500,"
                        + "\"error\":{\"type\":\"java.lang.NoClassDefFoundError\",\"message\":\"boom\"}}"));
        assertTrue(ex.getMessage().contains("java.lang.NoClassDefFoundError"));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void nonEnvelopeBodyIsSurfacedNotSwallowed() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("{\"success\":false,\"warnings\":[\"nope\"]}"));
        assertTrue(ex.getMessage().contains("no 'rows'"));
        assertTrue(ex.getMessage().contains("success"), "body snippet must be included for diagnosis");
    }

    @Test
    void nonJsonBodyIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("<html><body>System Exception 1129</body></html>"));
        assertTrue(ex.getMessage().contains("did not return JSON"));
    }
}
