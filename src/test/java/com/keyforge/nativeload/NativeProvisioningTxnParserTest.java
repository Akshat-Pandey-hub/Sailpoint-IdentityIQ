package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing (txn + nested items) + fail-loud behaviour for the native ProvisioningTransaction payload. */
class NativeProvisioningTxnParserTest {

    private final NativeProvisioningTxnParser parser = new NativeProvisioningTxnParser();

    @Test
    void parsesTxnWithNestedItemsAndReferences() {
        String json = "{\"sourceCount\":1,\"rows\":[{"
                + "\"sourceId\":\"t1\",\"identityName\":\"alice\",\"applicationName\":\"AD\","
                + "\"status\":\"Committed\",\"accessRequestId\":\"ar1\",\"certificationId\":\"c1\","
                + "\"itemCount\":2,\"items\":["
                + "{\"itemType\":\"ATTRIBUTE\",\"operation\":\"Add\",\"name\":\"memberOf\",\"value\":\"g1\",\"itemIndex\":0},"
                + "{\"itemType\":\"PERMISSION\",\"operation\":\"Add\",\"permissionTarget\":\"/f\",\"permissionRights\":\"read\",\"itemIndex\":1}"
                + "]}]}";
        List<NativeProvisioningTxnRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals(1, parser.sourceCount(json));
        NativeProvisioningTxnRecord t = rows.get(0);
        assertEquals("ar1", t.accessRequestId);
        assertEquals("c1", t.certificationId);
        assertEquals(2, t.items.size());
        assertEquals("ATTRIBUTE", t.items.get(0).itemType);
        assertEquals("t1", t.items.get(0).txnSourceId, "item inherits parent txn id when absent");
        assertEquals("sailpoint.object.ProvisioningPlan$AttributeRequest", t.items.get(0).srcObjectType);
        assertEquals("sailpoint.object.ProvisioningPlan$PermissionRequest", t.items.get(1).srcObjectType);
    }

    @Test
    void genuineEmptyReturnsEmpty() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void parsesRetryReferenceAndStructuredItemValue() {
        String json = "{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"txn-1\","
                + "\"retryRequestId\":\"retry-1\",\"lastRetry\":\"2026-09-24T00:00:00Z\","
                + "\"items\":[{\"itemType\":\"ATTRIBUTE\",\"name\":\"groups\","
                + "\"valueJson\":[\"a\",\"b\"],\"itemIndex\":0}]}]}";
        NativeProvisioningTxnRecord r = parser.parse(json).get(0);
        assertEquals("retry-1", r.retryRequestId);
        assertEquals("2026-09-24T00:00:00Z", r.lastRetry.toString());
        assertEquals("[\"a\",\"b\"]", r.items.get(0).valueJson);
    }

    @Test
    void missingAuthoritativeTransactionIdFailsLoudly() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"rows\":[{\"name\":\"no-id\"}]}"));
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"ProvisioningTransaction\"}"));
    }
}
