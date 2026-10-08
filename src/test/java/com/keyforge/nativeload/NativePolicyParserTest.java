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
    void parsesViolationOwnerTypeRuleAndTemplate() {
        List<NativePolicyRecord> rows = parser.parse(
                "{\"rows\":[{"
                        + "\"sourceId\":\"p1\",\"name\":\"SoD\",\"type\":\"SOD\","
                        + "\"violationOwnerType\":\"Rule\",\"violationOwnerRuleId\":\"r-1\","
                        + "\"violationOwnerRuleName\":\"Owner Rule\",\"template\":true},"
                        + "{\"sourceId\":\"p2\",\"name\":\"SoD2\",\"type\":\"SOD\",\"template\":false}]}");
        assertEquals(2, rows.size());
        NativePolicyRecord a = rows.get(0);
        assertEquals("Rule", a.violationOwnerType);
        assertEquals("r-1", a.violationOwnerRuleId);
        assertEquals("Owner Rule", a.violationOwnerRuleName);
        assertEquals(Boolean.TRUE, a.template);
        NativePolicyRecord b = rows.get(1);
        assertEquals(Boolean.FALSE, b.template);
        org.junit.jupiter.api.Assertions.assertNull(b.violationOwnerType);
        org.junit.jupiter.api.Assertions.assertNull(b.violationOwnerRuleId);
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
