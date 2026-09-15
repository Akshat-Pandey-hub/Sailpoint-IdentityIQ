package com.keyforge.iiq.incremental;

import java.time.Duration;
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
 * authoritative source-side change time, the persisted per-entity watermark, the overlap window, and
 * the requested mode, {@link #apply} decides inclusion with these rules:
 *
 * <ul>
 *   <li><b>Full mode, or no prior watermark (first run):</b> every record is kept.</li>
 *   <li><b>Incremental mode:</b> a record is kept when its change time is {@code >=} the
 *       <i>effective</i> watermark, where <b>effective&nbsp;=&nbsp;stored&nbsp;watermark&nbsp;&minus;&nbsp;overlap window</b>
 *       (KeyForge Connector Design &sect;4.1). The bound is <b>inclusive</b> so a record sharing the
 *       effective watermark's exact timestamp is <i>not</i> skipped. The overlap deliberately
 *       re-admits records committed slightly before the stored watermark (clock skew / late commits);
 *       the idempotent upsert absorbs that harmless re-processing. A record whose change time is
 *       unknown ({@code null}) is always kept — the filter never drops a record it cannot prove is
 *       old.</li>
 * </ul>
 *
 * <p>The new watermark is the maximum change time observed across <b>all extracted records</b> (not
 * only the kept ones), and never regresses below the prior <b>stored</b> watermark — the overlap
 * lowers only the selection boundary, never the watermark that is persisted. The caller advances the
 * stored watermark to this value only after a fully successful run (see
 * {@link WatermarkService#shouldAdvance}).
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

    /** Overload with no overlap window (equivalent to {@code overlap = Duration.ZERO}). */
    public static <T> Result<T> apply(List<T> records,
                                      Function<T, Instant> changeTime,
                                      Instant watermark,
                                      ExtractionMode mode) {
        return apply(records, changeTime, watermark, mode, Duration.ZERO);
    }

    public static <T> Result<T> apply(List<T> records,
                                      Function<T, Instant> changeTime,
                                      Instant watermark,
                                      ExtractionMode mode,
                                      Duration overlap) {
        List<T> source = records == null ? List.<T>of() : records;
        List<T> kept = new ArrayList<>();
        Instant maxSeen = null;

        // The selection boundary is the STORED watermark minus the overlap window (§4.1). It is null
        // in full mode or on the first run, meaning "keep everything".
        Instant boundary = effectiveWatermark(watermark, mode, overlap);

        for (T record : source) {
            Instant ct = changeTime.apply(record);
            maxSeen = SourceChangeTime.max(maxSeen, ct);

            boolean include;
            if (boundary == null) {
                include = true;                       // full load, or first-ever run for this entity
            } else if (ct == null) {
                include = true;                       // unknown change time -> never skip (conservative)
            } else {
                include = !ct.isBefore(boundary);     // ct >= (watermark - overlap), inclusive
            }
            if (include) {
                kept.add(record);
            }
        }

        // The persisted watermark is the high-water mark of the SOURCE across everything we saw, and
        // must never move backwards below the STORED watermark. The overlap lowered only the selection
        // boundary above; records re-admitted by it must not drag the stored watermark down.
        Instant newWatermark = maxSeen;
        if (watermark != null && (newWatermark == null || newWatermark.isBefore(watermark))) {
            newWatermark = watermark;
        }

        return new Result<>(kept, newWatermark, source.size(), kept.size(), source.size() - kept.size());
    }

    /**
     * The effective incremental selection boundary: {@code watermark - overlap} in incremental mode
     * with a stored watermark, otherwise {@code null} ("keep everything" — full mode or first run).
     * A null/zero/negative overlap collapses to the stored watermark itself.
     */
    public static Instant effectiveWatermark(Instant watermark, ExtractionMode mode, Duration overlap) {
        if (mode == ExtractionMode.FULL || watermark == null) {
            return null;
        }
        if (overlap == null || overlap.isZero() || overlap.isNegative()) {
            return watermark;
        }
        return watermark.minus(overlap);
    }
}
