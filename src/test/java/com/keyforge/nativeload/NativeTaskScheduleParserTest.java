package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The TaskSchedule parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeTaskScheduleParserTest {

    private final NativeTaskScheduleParser parser = new NativeTaskScheduleParser();

    @Test
    void validEnvelopeParsesScheduleFields() {
        List<NativeTaskScheduleRecord> rows = parser.parse(
                "{\"entity\":\"TaskSchedule\",\"returned\":1,\"rows\":[{"
                        + "\"sourceId\":\"abc\",\"name\":\"Nightly HR Aggregation\","
                        + "\"definitionName\":\"Account Aggregation\",\"state\":\"Executing\","
                        + "\"cronExpressions\":[\"0 0 1 * * ?\"],\"arguments\":{\"applications\":\"Delimited HR\"}}]}");
        assertEquals(1, rows.size());
        assertEquals("Account Aggregation", rows.get(0).definitionName);
        assertEquals("Executing", rows.get(0).state);
        assertTrue(rows.get(0).cronExpressionsJson.contains("0 0 1"));
        assertTrue(rows.get(0).argumentsJson.contains("Delimited HR"));
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"TaskSchedule\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"TaskSchedule\",\"status\":500,"
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
