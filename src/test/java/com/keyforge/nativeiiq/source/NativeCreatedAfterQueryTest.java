package com.keyforge.nativeiiq.source;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The created-after helper used by the append-only (event-shaped) extractors. As with
 * {@link NativeIncrementalQueryTest}, the filter-construction path itself needs IIQ's aspectj-woven
 * {@code QueryOptions} (not instantiable on the test classpath); it is compiled against the 8.4
 * {@code identityiq.jar} and verified live. Here: the property name and the null no-op contract.
 */
class NativeCreatedAfterQueryTest {

    @Test
    void boundsOnTheStandardCreatedProperty() {
        assertEquals("created", NativeIncrementalQuery.CREATED_PROPERTY);
        assertEquals("modified", NativeIncrementalQuery.MODIFIED_PROPERTY, "the existing CSS cursor is unchanged");
    }

    @Test
    void nullQueryOptionsIsANoOp() {
        assertNull(NativeIncrementalQuery.applyCreatedAfter(null, Date.from(Instant.now())));
    }
}
