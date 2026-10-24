package com.keyforge.nativeiiq.source;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the native CSS incremental query helper — the single place the server-side
 * {@code modified > watermark} filter is built. Covers ISO parsing / timezone preservation (TEST 9)
 * and the null-watermark no-op (full-scan / append-only safety, TEST 8). The
 * {@code applyModifiedAfter(QueryOptions, Date)} filter-construction path itself is verified directly
 * against the 8.4 {@code identityiq.jar} ({@code Filter.gt(String,Object)} + {@code addFilter}) and
 * exercised in the live run; it is not unit-asserted here because instantiating IIQ's
 * aspectj-woven {@code QueryOptions} outside a running IIQ fails with {@code NoClassDefFoundError:
 * org/aspectj/lang/Signature} on the test classpath.
 */
class NativeIncrementalQueryTest {

    @Test
    void nullWatermarkIsAFullScanNoOp() {
        // null qo + a date must not throw and returns null (no QueryOptions instantiated).
        assertNull(NativeIncrementalQuery.applyModifiedAfter(null, Date.from(Instant.now())));
    }

    @Test
    void parsesIsoInstantPreservingTheExactMoment() {
        Date d = NativeIncrementalQuery.parseIsoToDate("2026-01-01T00:00:00Z");
        assertNotNull(d);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), d.toInstant());
    }

    @Test
    void parsesIsoWithOffsetToTheSameInstant() {
        // 2026-01-01T05:30:00+05:30 == 2026-01-01T00:00:00Z — no silent truncation.
        Date d = NativeIncrementalQuery.parseIsoToDate("2026-01-01T05:30:00+05:30");
        assertNotNull(d);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), d.toInstant());
    }

    @Test
    void returnsNullForAbsentOrUnparseableValue() {
        assertNull(NativeIncrementalQuery.parseIsoToDate(null));
        assertNull(NativeIncrementalQuery.parseIsoToDate("   "));
        assertNull(NativeIncrementalQuery.parseIsoToDate("not-a-timestamp"));
    }

    @Test
    void onlyASuppliedButUnparseableBoundIsInvalid() {
        // absent / blank = no bound (ordinary request), unchanged
        assertFalse(NativeIncrementalQuery.isInvalid(null));
        assertFalse(NativeIncrementalQuery.isInvalid(""));
        assertFalse(NativeIncrementalQuery.isInvalid("   "));
        // every format accepted today stays valid
        assertFalse(NativeIncrementalQuery.isInvalid("2026-01-01T00:00:00Z"));
        assertFalse(NativeIncrementalQuery.isInvalid("2026-01-01T00:00:00.123456Z"));
        assertFalse(NativeIncrementalQuery.isInvalid("2026-01-01T05:30:00+05:30"));
        // supplied but not an instant -> invalid (rejected with 400, never an unfiltered scan)
        assertTrue(NativeIncrementalQuery.isInvalid("yesterday"));
        assertTrue(NativeIncrementalQuery.isInvalid("2026-02-30T00:00:00Z"), "impossible calendar date");
        assertTrue(NativeIncrementalQuery.isInvalid("2026-01-01"), "date without time is not an instant");
        assertTrue(NativeIncrementalQuery.isInvalid("1767225600000"), "epoch millis is not a supported format");
    }

    @Test
    void invalidTimestampMessageIsFixedAndDoesNotEchoInput() {
        String msg = NativeIncrementalQuery.invalidTimestamp().getMessage();
        assertTrue(msg.startsWith("invalid 'modifiedAfter'"));
        assertFalse(msg.contains("yesterday"));
    }
}
