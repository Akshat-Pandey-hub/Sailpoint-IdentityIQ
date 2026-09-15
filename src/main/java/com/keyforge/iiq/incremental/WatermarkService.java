package com.keyforge.iiq.incremental;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Thin orchestration over {@link WatermarkRepository}: read the current per-entity watermark before a
 * run, and advance it after one — but <b>only when the run fully succeeded</b>. A failed or partial
 * run must never move the watermark forward, or unprocessed changes would be permanently skipped on
 * the next incremental run.
 */
public class WatermarkService {

    private final WatermarkRepository repository;

    public WatermarkService() {
        this(new WatermarkRepository());
    }

    public WatermarkService(String schema) {
        this(new WatermarkRepository(schema));
    }

    public WatermarkService(WatermarkRepository repository) {
        this.repository = repository;
    }

    public WatermarkRepository repository() {
        return repository;
    }

    /** Ensures the table exists and returns the current watermark ({@code null} = first run). */
    public Instant readWatermark(Connection conn, String entity) throws SQLException {
        repository.ensureTargetTable(conn);
        return repository.read(conn, entity);
    }

    /**
     * Advances the watermark for {@code entity} to {@code newWatermark} iff {@link #shouldAdvance}.
     *
     * @return {@code true} if the watermark was written, {@code false} if intentionally left untouched
     */
    public boolean advanceIfSuccessful(Connection conn, String entity, String watermarkField,
                                       Instant newWatermark, int failed, String runId) throws SQLException {
        if (!shouldAdvance(failed, newWatermark)) {
            return false;
        }
        repository.ensureTargetTable(conn);
        repository.upsert(conn, entity, watermarkField, newWatermark, runId);
        return true;
    }

    /**
     * Pure advancement rule: advance only when there were zero row-level failures and there is an
     * actual watermark to store. Kept separate so the decision is trivially unit-testable.
     */
    public static boolean shouldAdvance(int failed, Instant newWatermark) {
        return failed == 0 && newWatermark != null;
    }
}
