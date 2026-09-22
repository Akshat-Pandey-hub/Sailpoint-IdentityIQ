package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Workgroup parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeWorkgroupParserTest {

    private final NativeWorkgroupParser parser = new NativeWorkgroupParser();

    @Test
    void validEnvelopeParsesRows() {
        List<NativeWorkgroupRecord> rows = parser.parse(
                "{\"entity\":\"Workgroup\",\"returned\":1,"
                        + "\"rows\":[{\"sourceId\":\"abc\",\"name\":\"Security Admins\",\"workgroup\":true}]}");
        assertEquals(1, rows.size());
        assertEquals("Security Admins", rows.get(0).getName());
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"Workgroup\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"Workgroup\",\"status\":500,"
                        + "\"error\":{\"type\":\"java.lang.NoClassDefFoundError\",\"message\":\"boom\"}}"));
        assertTrue(ex.getMessage().contains("java.lang.NoClassDefFoundError"));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void nonEnvelopeBodyIsSurfacedNotSwallowed() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("{\"success\":false}"));
        assertTrue(ex.getMessage().contains("no 'rows'"));
    }

    @Test
    void nonJsonBodyIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class,
                () -> parser.parse("<html>System Exception</html>"));
        assertTrue(ex.getMessage().contains("did not return JSON"));
    }
}
