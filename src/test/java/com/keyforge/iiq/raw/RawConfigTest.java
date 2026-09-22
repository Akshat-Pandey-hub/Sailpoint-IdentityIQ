package com.keyforge.iiq.raw;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAW is enabled only when {@code RAW_OUT_DIR} is set, following the env→system-property precedence of
 * the existing config. Absent → disabled (existing behavior preserved).
 */
class RawConfigTest {

    @Test
    void disabledWhenUnset() {
        // Only meaningful when the env var isn't set in the runner; the JVM property is cleared here.
        assumeTrue(System.getenv(RawConfig.KEY_OUT_DIR) == null);
        String prior = System.getProperty(RawConfig.KEY_OUT_DIR);
        System.clearProperty(RawConfig.KEY_OUT_DIR);
        try {
            assertTrue(RawConfig.loadIfEnabled().isEmpty(), "RAW must be disabled when RAW_OUT_DIR is unset");
        } finally {
            if (prior != null) {
                System.setProperty(RawConfig.KEY_OUT_DIR, prior);
            }
        }
    }

    @Test
    void enabledViaSystemProperty() {
        String prior = System.getProperty(RawConfig.KEY_OUT_DIR);
        System.setProperty(RawConfig.KEY_OUT_DIR, "build/test-raw-cfg");
        try {
            Optional<RawConfig> cfg = RawConfig.loadIfEnabled();
            assertTrue(cfg.isPresent(), "RAW enabled when RAW_OUT_DIR is set");
            assertEquals(Path.of("build/test-raw-cfg"), cfg.get().outputDir());
            assertEquals(Path.of("build/test-raw-cfg", "_runs"), cfg.get().runsDir());
        } finally {
            if (prior == null) {
                System.clearProperty(RawConfig.KEY_OUT_DIR);
            } else {
                System.setProperty(RawConfig.KEY_OUT_DIR, prior);
            }
        }
    }
}
