package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing + fail-loud behaviour for the native CertificationItem payload (incl. folded decision). */
class NativeCertificationItemParserTest {

    private final NativeCertificationItemParser parser = new NativeCertificationItemParser();

    @Test
    void parsesItemDecisionAndSourceCount() {
        String json = "{\"sourceCount\":1,\"rows\":[{"
                + "\"sourceId\":\"i1\",\"identity\":\"alice\",\"certificationId\":\"c1\","
                + "\"exceptionApplication\":\"AD\",\"exceptionAttributeName\":\"memberOf\","
                + "\"exceptionAttributeValue\":\"admins\",\"actionStatus\":\"Approved\","
                + "\"actionActorName\":\"spadmin\",\"actionIsApproved\":true,"
                + "\"applicationNames\":[\"AD\"]}]}";
        List<NativeCertificationItemRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals(1, parser.sourceCount(json));
        assertEquals("Approved", rows.get(0).actionStatus);
        assertEquals("spadmin", rows.get(0).actionActorName);
        assertEquals(Boolean.TRUE, rows.get(0).actionIsApproved);
        assertTrue(rows.get(0).applicationNamesJson.contains("AD"));
    }

    @Test
    void genuineEmptyReturnsEmpty() {
        assertTrue(parser.parse("{\"sourceCount\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorEnvelopeFailsLoudly() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("{\"entity\":\"CertificationItem\"}"));
    }
}
