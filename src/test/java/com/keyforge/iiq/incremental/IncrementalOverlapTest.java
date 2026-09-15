package com.keyforge.iiq.incremental;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Connector Design &sect;4.1 overlap window:
 * {@code effective_watermark = stored_watermark - overlap}, then inclusive
 * {@code source_change_time >= effective_watermark}. Pure — no DB, no IIQ.
 */
class IncrementalOverlapTest {

    private record Rec(String id, Instant changed) {
    }

    private static final Function<Rec, Instant> CHANGE = Rec::changed;

    private static Rec r(String id, String iso) {
        return new Rec(id, iso == null ? null : Instant.parse(iso));
    }

    private static final Instant WM = Instant.parse("2025-09-15T10:00:00Z");
    private static final Duration FIVE = Duration.ofMinutes(5);

    // effective watermark = stored - overlap (10:00 - 5m = 09:55).
    @Test
    void effectiveWatermarkIsStoredMinusOverlap() {
        assertEquals(Instant.parse("2025-09-15T09:55:00Z"),
                IncrementalFilter.effectiveWatermark(WM, ExtractionMode.INCREMENTAL, FIVE));
    }

    // Full mode and first-run (null watermark) => no boundary (keep everything).
    @Test
    void effectiveWatermarkNullInFullOrFirstRun() {
        assertNull(IncrementalFilter.effectiveWatermark(WM, ExtractionMode.FULL, FIVE));
        assertNull(IncrementalFilter.effectiveWatermark(null, ExtractionMode.INCREMENTAL, FIVE));
    }

    // Zero / null overlap collapses to the stored watermark itself (backwards-compatible).
    @Test
    void zeroOverlapCollapsesToStoredWatermark() {
        assertEquals(WM, IncrementalFilter.effectiveWatermark(WM, ExtractionMode.INCREMENTAL, Duration.ZERO));
        assertEquals(WM, IncrementalFilter.effectiveWatermark(WM, ExtractionMode.INCREMENTAL, null));
    }

    // Boundary is inclusive: a record exactly at the effective watermark (09:55) is INCLUDED;
    // a record just before it (09:54:59) is EXCLUDED.
    @Test
    void boundaryIsInclusiveAtEffectiveWatermark() {
        List<Rec> recs = List.of(
                r("at-effective", "2025-09-15T09:55:00Z"),
                r("just-before", "2025-09-15T09:54:59Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, WM, ExtractionMode.INCREMENTAL, FIVE);
        assertEquals(List.of("at-effective"), res.toPersist().stream().map(Rec::id).toList());
        assertEquals(1, res.skipped());
    }

    // Records inside the overlap window (between effective 09:55 and stored 10:00) are re-admitted
    // and reprocessed — safe because persistence is idempotent. The example from the task prompt.
    @Test
    void recordsInsideOverlapAreReprocessed() {
        List<Rec> recs = List.of(
                r("a", "2025-09-15T09:56:00Z"),
                r("b", "2025-09-15T09:58:00Z"),
                r("c", "2025-09-15T10:00:00Z"),
                r("d", "2025-09-15T10:03:00Z"),
                r("old", "2025-09-15T09:50:00Z"));   // before effective -> skipped
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, WM, ExtractionMode.INCREMENTAL, FIVE);
        assertEquals(List.of("a", "b", "c", "d"), res.toPersist().stream().map(Rec::id).toList());
        assertEquals(1, res.skipped());
    }

    // The persisted watermark still advances to the max source change time observed.
    @Test
    void watermarkAdvancesToMaxDespiteOverlap() {
        List<Rec> recs = List.of(
                r("a", "2025-09-15T09:56:00Z"),
                r("d", "2025-09-15T10:03:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, WM, ExtractionMode.INCREMENTAL, FIVE);
        assertEquals(Instant.parse("2025-09-15T10:03:00Z"), res.newWatermark());
    }

    // Crucial overlap interaction: a run that only re-sees overlap-window records (all < stored 10:00)
    // must NOT regress the persisted watermark below the stored value.
    @Test
    void watermarkDoesNotRegressWhenOnlyOverlapRecordsSeen() {
        List<Rec> recs = List.of(
                r("a", "2025-09-15T09:56:00Z"),
                r("b", "2025-09-15T09:58:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, WM, ExtractionMode.INCREMENTAL, FIVE);
        assertEquals(2, res.kept(), "overlap records are reprocessed");
        assertEquals(WM, res.newWatermark(), "watermark must not drop below the stored value");
    }

    // Failed processing does not advance the watermark (gate is independent of overlap).
    @Test
    void failedProcessingDoesNotAdvance() {
        assertTrue(WatermarkService.shouldAdvance(0, Instant.parse("2025-09-15T10:03:00Z")));
        assertFalse(WatermarkService.shouldAdvance(2, Instant.parse("2025-09-15T10:03:00Z")));
    }

    // UTC / OffsetDateTime correctness: an offset-bearing source time yields the same Instant boundary.
    @Test
    void overlapMathIsUtcAbsolute() {
        // 10:00Z expressed as 15:30 at +05:30 is the same instant; effective boundary is identical.
        Instant fromOffset = OffsetDateTime.of(2025, 9, 15, 15, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))
                .toInstant();
        assertEquals(WM, fromOffset);
        assertEquals(Instant.parse("2025-09-15T09:55:00Z"),
                IncrementalFilter.effectiveWatermark(fromOffset, ExtractionMode.INCREMENTAL, FIVE));
    }

    // Full mode ignores the overlap entirely and keeps everything.
    @Test
    void fullModeIgnoresOverlap() {
        List<Rec> recs = List.of(
                r("old", "2000-01-01T00:00:00Z"),
                r("new", "2025-09-15T10:03:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, WM, ExtractionMode.FULL, FIVE);
        assertEquals(2, res.kept());
        assertEquals(0, res.skipped());
    }
}
