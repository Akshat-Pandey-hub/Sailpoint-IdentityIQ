package com.keyforge.nativeiiq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure config behavior (no SailPoint dependency): defaults, overrides, and limit handling. */
class NativeExtractionConfigTest {

    @Test
    void defaultsWhenNoOverrides() {
        NativeExtractionConfig c = new NativeExtractionConfig(null, null, 0, -1, null);
        assertEquals("IdentityIQ", c.getSourceSystem());
        assertNotNull(c.getExtractionRunId(), "a run id is always generated");
        assertEquals(NativeExtractionConfig.DEFAULT_BATCH_SIZE, c.getBatchSize());
        assertEquals(0, c.getIdentityLimit());
        assertFalse(c.hasIdentityLimit());
        assertEquals("IN_MEMORY", c.getOutputMode());
    }

    @Test
    void explicitOverridesAreHonored() {
        NativeExtractionConfig c = NativeExtractionConfig.of("MyIIQ", Integer.valueOf(50),
                Integer.valueOf(3), "FILE", "run-123");
        assertEquals("MyIIQ", c.getSourceSystem());
        assertEquals(50, c.getBatchSize());
        assertEquals(3, c.getIdentityLimit());
        assertTrue(c.hasIdentityLimit());
        assertEquals("FILE", c.getOutputMode());
        assertEquals("run-123", c.getExtractionRunId());
    }

    @Test
    void nonPositiveBatchFallsBackToDefault() {
        assertEquals(NativeExtractionConfig.DEFAULT_BATCH_SIZE,
                new NativeExtractionConfig("s", "r", 0, 0, "m").getBatchSize());
    }
}
