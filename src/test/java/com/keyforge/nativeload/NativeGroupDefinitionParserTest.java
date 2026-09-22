package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The GroupDefinition parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeGroupDefinitionParserTest {

    private final NativeGroupDefinitionParser parser = new NativeGroupDefinitionParser();

    @Test
    void validEnvelopeParsesRowsPreservingType() {
        List<NativeGroupDefinitionRecord> rows = parser.parse(
                "{\"entity\":\"GroupDefinition\",\"returned\":2,\"rows\":["
                        + "{\"sourceId\":\"g1\",\"name\":\"Dept-HR\",\"type\":\"GROUP\",\"factoryName\":\"Department\"},"
                        + "{\"sourceId\":\"p1\",\"name\":\"Contractors\",\"type\":\"POPULATION\"}]}");
        assertEquals(2, rows.size());
        assertEquals("GROUP", rows.get(0).getType());
        assertEquals("POPULATION", rows.get(1).getType());
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"GroupDefinition\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"GroupDefinition\",\"status\":500,"
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
