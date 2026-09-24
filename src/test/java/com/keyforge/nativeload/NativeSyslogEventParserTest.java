package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing, sourceCount, raw-field preservation, null handling, and fail-loud for SyslogEvent. */
class NativeSyslogEventParserTest {

    private final NativeSyslogEventParser parser = new NativeSyslogEventParser();

    @Test
    void parsesSyslogFieldsAndSourceCount() {
        String json = "{\"entity\":\"SyslogEvent\",\"sourceCount\":40,\"returned\":1,\"rows\":[{"
                + "\"sourceId\":\"s1\",\"eventLevel\":\"ERROR\",\"server\":\"iiq01\",\"username\":\"spadmin\","
                + "\"message\":\"NPE in refresh\",\"quickKey\":\"qk-9\"}]}";
        assertEquals(40, parser.sourceCount(json));
        List<NativeSyslogEventRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals("ERROR", rows.get(0).eventLevel);
        assertEquals("iiq01", rows.get(0).server);
        assertEquals("spadmin", rows.get(0).username);   // raw actor name kept verbatim
        assertTrue(rows.get(0).message.contains("NPE"));
    }

    @Test
    void missingFieldsNullAndEmptyPageOk() {
        List<NativeSyslogEventRecord> rows =
                parser.parse("{\"sourceCount\":0,\"rows\":[{\"sourceId\":\"s2\",\"eventLevel\":\"INFO\"}]}");
        assertNull(rows.get(0).stacktrace);
        assertNull(rows.get(0).server);
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorAndNonJsonSurfaced() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>error</html>"));
        assertEquals(-1, parser.sourceCount("not json"));
    }
}
