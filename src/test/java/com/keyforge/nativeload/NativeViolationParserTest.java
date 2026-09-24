package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PolicyViolation parser must fail loudly on non-envelope/error bodies; a genuine empty page is ok. */
class NativeViolationParserTest {

    private final NativeViolationParser parser = new NativeViolationParser();

    @Test
    void parsesViolationWithExplicitReferences() {
        List<NativeViolationRecord> rows = parser.parse(
                "{\"entity\":\"PolicyViolation\",\"returned\":1,\"rows\":[{"
                        + "\"sourceId\":\"v1\",\"identityId\":\"id-1\",\"identityName\":\"alice\",\"policyId\":\"pol-1\","
                        + "\"policyName\":\"SoD\",\"constraintId\":\"c-1\",\"status\":\"Open\",\"active\":true,"
                        + "\"relevantApps\":[\"AD\"]}]}");
        assertEquals(1, rows.size());
        assertEquals("id-1", rows.get(0).identityId);
        assertEquals("pol-1", rows.get(0).policyId);
        assertEquals(Boolean.TRUE, rows.get(0).active);
        assertTrue(rows.get(0).relevantAppsJson.contains("AD"));
    }

    @Test
    void genuineEmptyPageReturnsEmpty() {
        assertTrue(parser.parse("{\"entity\":\"PolicyViolation\",\"returned\":0,\"rows\":[]}").isEmpty());
    }

    @Test
    void controlledErrorEnvelopeIsSurfaced() {
        NativeImportException ex = assertThrows(NativeImportException.class, () -> parser.parse(
                "{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void nonEnvelopeAndNonJsonSurfaced() {
        assertThrows(NativeImportException.class, () -> parser.parse("{\"success\":false}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>error</html>"));
    }
}
