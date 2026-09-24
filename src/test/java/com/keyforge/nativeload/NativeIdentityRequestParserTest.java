package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing (request + nested items + approval summaries) + fail-loud for the native IdentityRequest payload. */
class NativeIdentityRequestParserTest {

    private final NativeIdentityRequestParser parser = new NativeIdentityRequestParser();

    @Test
    void parsesRequestItemsAndApprovalEvidence() {
        String json = "{\"sourceCount\":1,\"rows\":[{"
                + "\"sourceId\":\"r1\",\"name\":\"0000123\",\"state\":\"executing\",\"requesterId\":\"u1\","
                + "\"targetId\":\"u2\",\"itemCount\":1,\"approvalCount\":1,"
                + "\"items\":[{\"sourceId\":\"ri1\",\"requestSourceId\":\"r1\",\"application\":\"AD\","
                + "\"attributeName\":\"memberOf\",\"attributeValue\":\"admins\",\"operation\":\"Add\","
                + "\"approverName\":\"mgr\",\"expansionCause\":\"Role\"}],"
                + "\"approvals\":[{\"requestSourceId\":\"r1\",\"workItemId\":\"wi1\",\"owner\":\"mgr\","
                + "\"ownerId\":\"u9\",\"approved\":true,\"state\":\"Finished\",\"approvalIndex\":0,"
                + "\"comments\":[{\"author\":\"mgr\",\"comment\":\"ok\"}]}]}]}";
        List<NativeIdentityRequestRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals(1, parser.sourceCount(json));
        NativeIdentityRequestRecord r = rows.get(0);
        assertEquals(1, r.items.size());
        assertEquals(1, r.approvals.size());
        assertEquals("r1", r.items.get(0).requestSourceId);
        assertEquals("Role", r.items.get(0).expansionCause);
        assertEquals("wi1", r.approvals.get(0).workItemId);
        assertEquals("mgr", r.approvals.get(0).owner);
        assertTrue(r.approvals.get(0).commentsJson.contains("ok"));
    }

    @Test
    void genuineEmptyReturnsEmpty() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"IdentityRequest\"}"));
    }
}
