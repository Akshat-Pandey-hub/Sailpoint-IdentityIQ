package com.keyforge.iiq.incremental;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the incremental-extraction correctness core ({@link IncrementalFilter},
 * {@link WatermarkService#shouldAdvance}, {@link WatermarkRepository} schema handling,
 * {@link SourceChangeTime}). No database or IdentityIQ is touched — the logic that governs which
 * records are persisted and how the watermark moves is exercised directly.
 */
class IncrementalExtractionTest {

    /** A minimal record carrying an id and a source-side change instant. */
    private record Rec(String id, Instant changed) {
    }

    private static final Function<Rec, Instant> CHANGE = Rec::changed;

    private static Rec r(String id, String iso) {
        return new Rec(id, iso == null ? null : Instant.parse(iso));
    }

    // 1. First run / no watermark: incremental behaves like full — every record is kept.
    @Test
    void firstRunNoWatermarkKeepsAll() {
        List<Rec> recs = List.of(
                r("a", "2025-01-01T00:00:00Z"),
                r("b", "2025-06-01T00:00:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, null, ExtractionMode.INCREMENTAL);
        assertEquals(2, res.kept());
        assertEquals(0, res.skipped());
        assertEquals(Instant.parse("2025-06-01T00:00:00Z"), res.newWatermark());
    }

    // 2. Successful incremental run: only records at/after the watermark are kept.
    @Test
    void incrementalKeepsOnlyChanged() {
        List<Rec> recs = List.of(
                r("old", "2025-01-01T00:00:00Z"),
                r("new1", "2025-08-01T00:00:00Z"),
                r("new2", "2025-09-01T00:00:00Z"));
        Instant wm = Instant.parse("2025-07-01T00:00:00Z");
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, wm, ExtractionMode.INCREMENTAL);
        assertEquals(2, res.kept());
        assertEquals(1, res.skipped());
        assertEquals(List.of("new1", "new2"), res.toPersist().stream().map(Rec::id).toList());
    }

    // 3. Watermark advances to the max source change time across ALL extracted records.
    @Test
    void watermarkAdvancesToMaxSourceChangeTime() {
        List<Rec> recs = List.of(
                r("a", "2025-03-01T00:00:00Z"),
                r("b", "2025-09-15T12:34:56Z"),
                r("c", "2025-05-01T00:00:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, Instant.parse("2025-01-01T00:00:00Z"),
                        ExtractionMode.INCREMENTAL);
        assertEquals(Instant.parse("2025-09-15T12:34:56Z"), res.newWatermark());
    }

    // 4. A failed/partial run must NOT advance the watermark.
    @Test
    void failedRunDoesNotAdvance() {
        Instant candidate = Instant.parse("2025-09-15T00:00:00Z");
        assertTrue(WatermarkService.shouldAdvance(0, candidate), "clean run with a watermark advances");
        assertFalse(WatermarkService.shouldAdvance(1, candidate), "any row failure blocks advancement");
        assertFalse(WatermarkService.shouldAdvance(0, null), "no watermark -> nothing to advance");
    }

    // 5. Pagination: the filter operates on the fully-assembled list and loses nothing at page joins.
    @Test
    void paginationFiltersFullAssembledListWithoutLoss() {
        // Simulate 250 records assembled from 3 SCIM pages (100/100/50), interleaved old/new.
        java.util.List<Rec> recs = new java.util.ArrayList<>();
        Instant wm = Instant.parse("2025-06-01T00:00:00Z");
        int expectedKept = 0;
        for (int i = 0; i < 250; i++) {
            // even -> before watermark (skip), odd -> after watermark (keep)
            String iso = (i % 2 == 0) ? "2025-01-01T00:00:00Z" : "2025-12-01T00:00:00Z";
            if (i % 2 != 0) {
                expectedKept++;
            }
            recs.add(r("r" + i, iso));
        }
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, wm, ExtractionMode.INCREMENTAL);
        assertEquals(250, res.total());
        assertEquals(expectedKept, res.kept());
        assertEquals(250 - expectedKept, res.skipped());
    }

    // 6. Same-timestamp boundary: a record whose change time EQUALS the watermark is kept, not skipped.
    @Test
    void sameTimestampBoundaryRecordIsKept() {
        Instant wm = Instant.parse("2025-07-01T00:00:00Z");
        List<Rec> recs = List.of(
                r("boundary", "2025-07-01T00:00:00Z"),   // exactly at the watermark
                r("older", "2025-06-30T23:59:59Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, wm, ExtractionMode.INCREMENTAL);
        assertEquals(List.of("boundary"), res.toPersist().stream().map(Rec::id).toList());
        assertEquals(1, res.skipped());
    }

    // 7. Idempotent re-run: after advancing to the max, re-running keeps only the boundary record(s),
    //    drops nothing older by mistake, and does not regress the watermark.
    @Test
    void idempotentReRunAfterAdvanceKeepsBoundaryOnly() {
        List<Rec> recs = List.of(
                r("a", "2025-01-01T00:00:00Z"),
                r("max", "2025-09-01T00:00:00Z"));
        Instant advanced = Instant.parse("2025-09-01T00:00:00Z"); // watermark after a prior full run
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, advanced, ExtractionMode.INCREMENTAL);
        assertEquals(List.of("max"), res.toPersist().stream().map(Rec::id).toList());
        assertEquals(advanced, res.newWatermark(), "watermark must not regress on a quiet re-run");
    }

    // 8. Empty incremental result: nothing to persist, and the watermark is left unchanged (not nulled).
    @Test
    void emptyIncrementalResultLeavesWatermarkUnchanged() {
        Instant wm = Instant.parse("2025-07-01T00:00:00Z");
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(List.of(), CHANGE, wm, ExtractionMode.INCREMENTAL);
        assertEquals(0, res.total());
        assertEquals(0, res.kept());
        assertEquals(wm, res.newWatermark());
    }

    // 9. Schema handling: the watermark repository honours PG_SCHEMA in its table name and DDL.
    @Test
    void watermarkRepositoryRespectsPgSchema() {
        WatermarkRepository repo = new WatermarkRepository("migration_final");
        assertEquals("migration_final", repo.schema());
        assertEquals("migration_final.kf_extraction_watermark", repo.targetTable());
        assertTrue(repo.createTableSql().contains("migration_final.kf_extraction_watermark"));
        assertTrue(repo.createTableSql().contains("source_watermark timestamptz"));
    }

    // --- extra guards on the supporting pieces ------------------------------

    // Full mode ignores the watermark entirely and keeps everything (validated path unchanged).
    @Test
    void fullModeKeepsEverythingRegardlessOfWatermark() {
        List<Rec> recs = List.of(
                r("old", "2020-01-01T00:00:00Z"),
                r("new", "2025-01-01T00:00:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, Instant.parse("2099-01-01T00:00:00Z"),
                        ExtractionMode.FULL);
        assertEquals(2, res.kept());
        assertEquals(0, res.skipped());
    }

    // A null / unparseable change time is never skipped in incremental mode.
    @Test
    void unknownChangeTimeIsNeverSkipped() {
        List<Rec> recs = List.of(
                r("noTime", null),
                r("old", "2020-01-01T00:00:00Z"));
        IncrementalFilter.Result<Rec> res =
                IncrementalFilter.apply(recs, CHANGE, Instant.parse("2025-01-01T00:00:00Z"),
                        ExtractionMode.INCREMENTAL);
        assertTrue(res.toPersist().stream().anyMatch(x -> x.id().equals("noTime")));
        assertFalse(res.toPersist().stream().anyMatch(x -> x.id().equals("old")));
    }

    @Test
    void sourceChangeTimeParsesScimUtcAndDetectsMax() {
        Instant a = SourceChangeTime.parseIso("2025-07-27T04:00:28.298Z");
        Instant b = SourceChangeTime.parseIso("2025-07-27T04:00:28.321Z");
        assertEquals(Instant.parse("2025-07-27T04:00:28.298Z"), a);
        assertEquals(b, SourceChangeTime.max(a, b));
        assertSame(a, SourceChangeTime.max(a, null));
        assertEquals(null, SourceChangeTime.parseIso("  "));
        assertEquals(null, SourceChangeTime.parseIso("not-a-date"));
    }

    @Test
    void extractionModeParsesFlags() {
        assertEquals(ExtractionMode.INCREMENTAL,
                ExtractionMode.fromArgs(new String[] {"extract-users-db", "--incremental"}));
        assertEquals(ExtractionMode.FULL, ExtractionMode.fromArgs(new String[] {"extract-users-db"}));
        assertEquals(ExtractionMode.FULL, ExtractionMode.fromArgs(new String[] {"extract-users-db", "--full"}));
    }
}
