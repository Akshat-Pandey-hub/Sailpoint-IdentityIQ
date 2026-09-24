package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing (flat WorkItem + nested jsonb evidence) + fail-loud for the native WorkItem payload. */
class NativeWorkItemParserTest {

    private final NativeWorkItemParser parser = new NativeWorkItemParser();

    @Test
    void parsesWorkItemWithRequestLinkAndApprovalEvidence() {
        String json = "{\"sourceCount\":1,\"rows\":[{"
                + "\"sourceId\":\"wi1\",\"name\":\"Approval 1\",\"type\":\"Approval\",\"state\":\"Pending\","
                + "\"identityRequestId\":\"0000123\",\"ownerName\":\"mgr\","
                + "\"comments\":[{\"author\":\"mgr\",\"comment\":\"ok\"}],"
                + "\"approvalSetItems\":[{\"applicationName\":\"AD\",\"displayValue\":\"admins\",\"operation\":\"Add\"}]}]}";
        List<NativeWorkItemRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals(1, parser.sourceCount(json));
        NativeWorkItemRecord w = rows.get(0);
        assertEquals("0000123", w.identityRequestId);
        assertEquals("Pending", w.state);
        assertTrue(w.commentsJson.contains("ok"));
        assertTrue(w.approvalSetItemsJson.contains("admins"));
    }

    @Test
    void genuineEmptyReturnsEmpty() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"WorkItem\"}"));
    }
}
