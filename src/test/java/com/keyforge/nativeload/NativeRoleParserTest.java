package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Role parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeRoleParserTest {

    private final NativeRoleParser parser = new NativeRoleParser();

    @Test
    void validEnvelopeParsesRows() {
        List<NativeRoleRecord> rows = parser.parse(
                "{\"entity\":\"Bundle\",\"returned\":1,"
                        + "\"rows\":[{\"sourceId\":\"abc\",\"name\":\"Engineer\",\"type\":\"business\"}]}");
        assertEquals(1, rows.size());
        assertEquals("Engineer", rows.get(0).getName());
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"Bundle\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"Bundle\",\"status\":500,"
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
