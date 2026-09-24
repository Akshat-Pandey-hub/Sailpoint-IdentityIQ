package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing, sourceCount, raw-reference preservation, null handling, and fail-loud for AuditEvent. */
class NativeAuditEventParserTest {

    private final NativeAuditEventParser parser = new NativeAuditEventParser();

    @Test
    void parsesAuditFieldsAndSourceCountRawVerbatim() {
        String json = "{\"entity\":\"AuditEvent\",\"sourceCount\":322,\"returned\":1,\"rows\":[{"
                + "\"sourceId\":\"a1\",\"action\":\"ManageAttribute\",\"auditSource\":\"spadmin\","
                + "\"target\":\"CN=Bob\",\"application\":\"AD\",\"trackingId\":\"trk-9\","
                + "\"attributes\":{\"k\":\"v\"}}]}";
        List<NativeAuditEventRecord> rows = parser.parse(json);
        assertEquals(322, parser.sourceCount(json));
        assertEquals(1, rows.size());
        assertEquals("ManageAttribute", rows.get(0).action);
        assertEquals("spadmin", rows.get(0).auditSource);
        assertEquals("CN=Bob", rows.get(0).target);   // raw name kept verbatim, not resolved
        assertEquals("trk-9", rows.get(0).trackingId);
        assertTrue(rows.get(0).attributesJson.contains("\"k\""));
    }

    @Test
    void missingFieldsAreNullAndEmptyPageOk() {
        List<NativeAuditEventRecord> rows =
                parser.parse("{\"sourceCount\":0,\"rows\":[{\"sourceId\":\"a2\",\"action\":\"Login\"}]}");
        assertNull(rows.get(0).target);
        assertNull(rows.get(0).application);
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
        assertEquals(0, parser.sourceCount("{\"sourceCount\":0,\"rows\":[]}"));
    }

    @Test
    void errorAndNonJsonSurfaced() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>error</html>"));
        assertEquals(-1, parser.sourceCount("not json"));
    }
}
