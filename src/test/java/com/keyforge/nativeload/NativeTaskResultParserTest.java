package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The TaskResult parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeTaskResultParserTest {

    private final NativeTaskResultParser parser = new NativeTaskResultParser();

    @Test
    void validEnvelopeParsesRunFields() {
        List<NativeTaskResultRecord> rows = parser.parse(
                "{\"entity\":\"TaskResult\",\"returned\":1,\"rows\":[{"
                        + "\"sourceId\":\"abc\",\"name\":\"Account Aggregation\",\"type\":\"AccountAggregation\","
                        + "\"completionStatus\":\"Success\",\"percentComplete\":100,"
                        + "\"messages\":[{\"key\":\"task_done\",\"type\":\"Info\"}],"
                        + "\"attributes\":{\"applications\":\"Delimited HR\"}}]}");
        assertEquals(1, rows.size());
        assertEquals("AccountAggregation", rows.get(0).type);
        assertEquals("Success", rows.get(0).completionStatus);
        assertEquals(Integer.valueOf(100), rows.get(0).percentComplete);
        assertTrue(rows.get(0).messagesJson.contains("task_done"));
        assertTrue(rows.get(0).attributesJson.contains("Delimited HR"));
    }

    @Test
    void genuineEmptyPageReturnsEmptyWithoutError() {
        assertTrue(parser.parse("{\"entity\":\"TaskResult\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"entity\":\"TaskResult\",\"status\":500,"
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
