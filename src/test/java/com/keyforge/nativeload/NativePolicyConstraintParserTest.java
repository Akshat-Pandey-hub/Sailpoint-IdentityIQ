package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing SoD/generic constraint rows, explicit parent + bundle refs, counts, and fail-loud. */
class NativePolicyConstraintParserTest {

    private final NativePolicyConstraintParser parser = new NativePolicyConstraintParser();

    @Test
    void parsesSodConstraintWithExplicitParentAndBundles() {
        String json = "{\"entity\":\"PolicyConstraint\",\"sourceCount\":7,\"returnedPolicies\":7,\"rows\":[{"
                + "\"sourceId\":\"c1\",\"policyId\":\"pol-1\",\"policyName\":\"SoD\",\"name\":\"Cash vs Approve\","
                + "\"constraintType\":\"SOD\",\"weight\":5,"
                + "\"leftBundles\":[{\"id\":\"r1\",\"name\":\"Cashier\"}],"
                + "\"rightBundles\":[{\"id\":\"r2\",\"name\":\"Approver\"}]}]}";
        assertEquals(7, parser.sourceCount(json));
        assertEquals(7, parser.returnedPolicies(json));
        List<NativePolicyConstraintRecord> rows = parser.parse(json);
        assertEquals(1, rows.size());
        assertEquals("pol-1", rows.get(0).policyId);          // explicit parent
        assertEquals("SOD", rows.get(0).constraintType);
        assertTrue(rows.get(0).leftBundlesJson.contains("r1"));   // explicit conflicting role id
        assertTrue(rows.get(0).rightBundlesJson.contains("r2"));
    }

    @Test
    void parsesGenericConstraintSelectors() {
        List<NativePolicyConstraintRecord> rows = parser.parse(
                "{\"sourceCount\":1,\"returnedPolicies\":1,\"rows\":[{\"sourceId\":\"c2\",\"policyId\":\"p\","
                        + "\"constraintType\":\"GENERIC\",\"selectorCount\":2,\"selectors\":[\"a\",\"b\"]}]}");
        assertEquals(Integer.valueOf(2), rows.get(0).selectorCount);
        assertTrue(rows.get(0).selectorsJson.contains("a"));
    }

    @Test
    void emptyPageAndFailLoud() {
        assertTrue(parser.parse("{\"sourceCount\":1,\"returnedPolicies\":1,\"rows\":[]}").isEmpty());
        assertThrows(NativeImportException.class,
                () -> parser.parse("{\"status\":500,\"error\":{\"type\":\"X\",\"message\":\"boom\"}}"));
        assertThrows(NativeImportException.class, () -> parser.parse("<html>error</html>"));
    }
}
