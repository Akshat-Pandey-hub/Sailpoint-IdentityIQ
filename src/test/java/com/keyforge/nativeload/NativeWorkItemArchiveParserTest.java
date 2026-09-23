package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeWorkItemArchiveParserTest {
    private final NativeWorkItemArchiveParser parser = new NativeWorkItemArchiveParser();

    @Test
    void parsesArchiveAndSourceCount() {
        List<NativeWorkItemArchiveRecord> rows = parser.parse("{\"sourceCount\":1,\"rows\":[{"
                + "\"sourceId\":\"7f0001019f061fdc819fa50301271752\",\"archived\":\"2026-09-01T10:11:12Z\","
                + "\"srcEventTs\":\"2026-09-01T10:11:12Z\",\"signOffs\":[{\"signerName\":\"A\"}]}]}");
        assertEquals(1, rows.size());
        assertEquals("7f0001019f061fdc819fa50301271752", rows.get(0).getSourceId());
        assertEquals(rows.get(0).getArchived(), rows.get(0).getSrcEventTs());
        assertEquals(1, parser.sourceCount("{\"sourceCount\":1,\"rows\":[]}"));
    }

    @Test
    void genuineEmptyEnvelopeIsValid() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
        assertEquals(0, parser.sourceCount("{\"sourceCount\":0,\"rows\":[]}"));
    }

    @Test
    void rejectsErrorMalformedAndDoubleEncodedBodies() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"error\":{\"type\":\"denied\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>failure</html>"));
        assertThrows(NativeImportException.class, () -> parser.parse("\"{\\\"rows\\\":[]}\""));
    }
}
