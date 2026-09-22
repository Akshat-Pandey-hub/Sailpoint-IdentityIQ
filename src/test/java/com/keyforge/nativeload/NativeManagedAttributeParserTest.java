package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ManagedAttribute parser must never let an IIQ error (or any non-envelope body) masquerade as an
 * empty page; a genuine empty page still returns empty cleanly.
 */
class NativeManagedAttributeParserTest {

    private final NativeManagedAttributeParser parser = new NativeManagedAttributeParser();

    @Test
    void validEnvelopeParsesRows() {
        List<NativeManagedAttributeRecord> rows = parser.parse(
                "{\"entity\":\"ManagedAttribute\",\"returned\":1,"
                        + "\"rows\":[{\"sourceId\":\"abc\",\"value\":\"CN=Admins\",\"name\":\"CN=Admins\"}]}");
        assertEquals(1, rows.size());
        assertEquals("abc", rows.get(0).getSourceId());
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"ManagedAttribute\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"ManagedAttribute\",\"status\":500,"
                        + "\"error\":{\"type\":\"java.lang.NoClassDefFoundError\",\"message\":\"boom\"}}"));
        assertTrue(ex.getMessage().contains("java.lang.NoClassDefFoundError"));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void nonEnvelopeBodyIsSurfacedNotSwallowed() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("{\"success\":false}"));
        assertTrue(ex.getMessage().contains("no 'rows'"));
        assertTrue(ex.getMessage().contains("success"));
    }

    @Test
    void nonJsonBodyIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("<html>System Exception</html>"));
        assertTrue(ex.getMessage().contains("did not return JSON"));
    }
}
