package com.keyforge.iiq.incremental;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The correctness core of incremental extraction: a pure, side-effect-free selection of which
 * already-extracted records must be persisted, plus the watermark those records imply. No database,
 * no HTTP, no clock — fully unit-testable.
 *
 * <p>Given the records a service already paged in, an extractor that yields each record's
 * authoritative source-side change time, the persisted per-entity watermark, and the requested mode,
 * {@link #apply} decides inclusion with these rules:
 *
 * <ul>
 *   <li><b>Full mode, or no prior watermark (first run):</b> every record is kept.</li>
 *   <li><b>Incremental mode:</b> a record is kept when its change time is {@code >=} the watermark.
 *       The bound is <b>inclusive</b> so that records sharing the watermark's exact timestamp are
 *       <i>not</i> skipped (the idempotent upsert absorbs the harmless re-processing of that
 *       boundary). A record whose change time is unknown ({@code null}) is always kept — the filter
 *       never drops a record it cannot prove is old.</li>
 * </ul>
 *
 * <p>The new watermark is the maximum change time observed across <b>all extracted records</b> (not
 * only the kept ones), and never regresses below the prior watermark. The caller advances the stored
 * watermark to this value only after a fully successful run (see {@link WatermarkService#shouldAdvance}).
 */
public final class IncrementalFilter {

    private IncrementalFilter() {
    }

    /**
     * @param toPersist   records selected for persistence (order preserved)
     * @param newWatermark the watermark implied by this run's source data (may be {@code null} only
     *                     when no record carried a change time and there was no prior watermark)
     * @param total       records considered
     * @param kept        records selected
     * @param skipped     records excluded as unchanged (always {@code total - kept})
     */
    public record Result<T>(List<T> toPersist, Instant newWatermark, int total, int kept, int skipped) {
    }

    public static <T> Result<T> apply(List<T> records,
                                      Function<T, Instant> changeTime,
                                      Instant watermark,
                                      ExtractionMode mode) {
        List<T> source = records == null ? List.<T>of() : records;
        List<T> kept = new ArrayList<>();
        Instant maxSeen = null;

        for (T record : source) {
            Instant ct = changeTime.apply(record);
            maxSeen = SourceChangeTime.max(maxSeen, ct);

            boolean include;
            if (mode == ExtractionMode.FULL || watermark == null) {
                include = true;                       // full load, or first-ever run for this entity
            } else if (ct == null) {
                include = true;                       // unknown change time -> never skip (conservative)
            } else {
                include = !ct.isBefore(watermark);    // ct >= watermark (inclusive lower boundary)
            }
            if (include) {
                kept.add(record);
            }
        }

        // Watermark is the high-water mark of the SOURCE across everything we saw, and must never
        // move backwards (an incremental run that happens to see only older/equal records keeps it).
        Instant newWatermark = maxSeen;
        if (watermark != null && (newWatermark == null || newWatermark.isBefore(watermark))) {
            newWatermark = watermark;
        }

        return new Result<>(kept, newWatermark, source.size(), kept.size(), source.size() - kept.size());
    }
}
