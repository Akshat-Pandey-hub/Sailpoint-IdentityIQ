package com.keyforge.iiq.incremental;

import com.keyforge.iiq.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link IncrementalConfig#parseOverlapMinutes} — the pure parse of
 * {@code INCREMENTAL_OVERLAP_MINUTES}. Environment resolution itself is a thin env/system-property
 * lookup and is not exercised here to keep the test hermetic.
 */
class IncrementalConfigTest {

    @Test
    void nullOrBlankTakesProjectDefault() {
        Duration expected = Duration.ofMinutes(IncrementalConfig.DEFAULT_OVERLAP_MINUTES);
        assertEquals(expected, IncrementalConfig.parseOverlapMinutes(null));
        assertEquals(expected, IncrementalConfig.parseOverlapMinutes(""));
        assertEquals(expected, IncrementalConfig.parseOverlapMinutes("   "));
    }

    @Test
    void parsesWholeMinutes() {
        assertEquals(Duration.ofMinutes(10), IncrementalConfig.parseOverlapMinutes("10"));
        assertEquals(Duration.ofMinutes(10), IncrementalConfig.parseOverlapMinutes("  10 "));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(Duration.ZERO, IncrementalConfig.parseOverlapMinutes("0"));
    }

    @Test
    void negativeIsRejected() {
        assertThrows(ConfigException.class, () -> IncrementalConfig.parseOverlapMinutes("-1"));
    }

    @Test
    void nonNumericIsRejected() {
        assertThrows(ConfigException.class, () -> IncrementalConfig.parseOverlapMinutes("5m"));
        assertThrows(ConfigException.class, () -> IncrementalConfig.parseOverlapMinutes("abc"));
    }

    @Test
    void defaultIsFiveMinutes() {
        assertEquals(5, IncrementalConfig.DEFAULT_OVERLAP_MINUTES);
    }
}
