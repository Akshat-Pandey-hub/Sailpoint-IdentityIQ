package com.keyforge.iiq.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** KFAGENT_IIQ_TIMEOUT_SECONDS: unset keeps today's behaviour; a set value is validated and drives the SQL timeout. */
class IiqTimeoutsTest {

    @Test
    void notSetKeepsTheDefaults() {
        assertNull(IiqTimeouts.parse(null));
        assertNull(IiqTimeouts.parse("   "));
        assertEquals(Duration.ofSeconds(60), IiqTimeouts.requestTimeout(null));
        assertNull(IiqTimeouts.sqlReportQueryTimeout(null), "the plugin keeps its own 50 s default");
    }

    @Test
    void aSetValueIsUsedAndTheSqlQueryStopsTenSecondsEarlier() {
        assertEquals(Integer.valueOf(300), IiqTimeouts.parse(" 300 "));
        assertEquals(Duration.ofSeconds(300), IiqTimeouts.requestTimeout(300));
        assertEquals(Integer.valueOf(290), IiqTimeouts.sqlReportQueryTimeout(300));
        assertEquals(Integer.valueOf(10), IiqTimeouts.sqlReportQueryTimeout(IiqTimeouts.MIN_SECONDS));
    }

    @Test
    void anInvalidValueFailsClearly() {
        assertThrows(IllegalArgumentException.class, () -> IiqTimeouts.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> IiqTimeouts.parse("1.5"));
        assertThrows(IllegalArgumentException.class, () -> IiqTimeouts.parse("19"));
        assertThrows(IllegalArgumentException.class, () -> IiqTimeouts.parse("3601"));
        assertEquals(Integer.valueOf(20), IiqTimeouts.parse("20"));
        assertEquals(Integer.valueOf(3600), IiqTimeouts.parse("3600"));
    }
}
