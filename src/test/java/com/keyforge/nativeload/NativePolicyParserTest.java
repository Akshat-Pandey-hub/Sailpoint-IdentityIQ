package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Policy parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativePolicyParserTest {

    private final NativePolicyParser parser = new NativePolicyParser();

    @Test
    void parsesPolicyDefinition() {
        List<NativePolicyRecord> rows = parser.parse(
                "{\"entity\":\"Policy\",\"returned\":1,\"rows\":[{"
                        + "\"sourceId\":\"p1\",\"name\":\"SoD\",\"type\":\"SOD\",\"executor\":\"sailpoint.SODPolicyExecutor\","
                        + "\"violationOwnerId\":\"o-1\",\"constraintCount\":3}]}");
        assertEquals(1, rows.size());
        assertEquals("SOD", rows.get(0).type);
        assertEquals("o-1", rows.get(0).violationOwnerId);
        assertEquals(Integer.valueOf(3), rows.get(0).constraintCount);
    }

    @Test
    void genuineEmptyPageReturnsEmpty() {
        assertTrue(parser.parse("{\"entity\":\"Policy\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void errorAndNonJsonSurfaced() {
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>error</html>"));
    }
}
