package com.keyforge.nativeiiq.source;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangedChildParentLimitTest {

    @Test
    void roleRelationshipFallsBackOnlyAfterMoreThanOneThousandChangedParents() {
        Set<String> ids = ids(1000);
        assertFalse(NativeIncrementalQuery.exceedsChangedParentIdLimit(ids));
        ids.add("parent-1001");
        assertTrue(NativeIncrementalQuery.exceedsChangedParentIdLimit(ids));
    }

    @Test
    void policyConstraintFallsBackOnlyAfterMoreThanOneThousandChangedParents() {
        Set<String> ids = ids(1000);
        assertFalse(NativeIncrementalQuery.exceedsChangedParentIdLimit(ids));
        ids.add("parent-1001");
        assertTrue(NativeIncrementalQuery.exceedsChangedParentIdLimit(ids));
    }

    private static Set<String> ids(int count) {
        Set<String> ids = new LinkedHashSet<String>();
        for (int i = 0; i < count; i++) ids.add("parent-" + i);
        return ids;
    }
}
