package com.keyforge.nativeload;

import com.keyforge.iiq.incremental.ExtractionMode;
import com.keyforge.iiq.incremental.IncrementalConfig;
import com.keyforge.iiq.incremental.IncrementalFilter;
import com.keyforge.iiq.incremental.WatermarkService;
import com.keyforge.iiq.runledger.RunLedger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * The single reusable native-layer <b>CSS (Current-State Sync) incremental</b> engine. It owns the
 * per-entity incremental <i>policy</i> defined by the KeyForge Connector Design &sect;4.1 / &sect;10 —
 * mode selection, the per-(object&nbsp;type&nbsp;&times;&nbsp;mode) watermark, the overlap window, the
 * server-side {@code modified}-bound hand-off, success/failure watermark advancement, and the run-ledger
 * record — so that <b>no native extractor reimplements any of it</b>. It is to incremental extraction
 * what {@link com.keyforge.iiq.deletion.SoftDeleteSweeper} is to deletion and {@link RunLedger} is to
 * run control: one mechanism, consumed by each vertical through a thin call.
 *
 * <p>The only entity-specific part is the <b>source query + persistence</b>, supplied as a {@link Loader}
 * callback: the engine hands it the computed incremental bound (ISO-8601, or {@code null} for a full
 * scan) and the {@code incremental} flag; the callback points the entity's client at that bound, runs
 * the entity's existing paged import (which carries the shared paging and the shared
 * {@code SoftDeleteSweeper}), and returns the row counts. The engine then records the run and advances
 * the watermark.
 *
 * <p><b>Native vs REST.</b> Unlike the SCIM path (no server-side "modified since" filter, so it reads
 * every page and filters client-side), the native path pushes the bound into IIQ's own query
 * ({@code Filter.gt("modified", ...)} via {@link com.keyforge.nativeiiq.source.NativeIncrementalQuery}),
 * so the plugin returns only changed objects. This engine is the client-side half that decides the bound
 * and maintains the watermark; the server-side half is the shared filter helper.
 *
 * <p><b>Deletion.</b> Per &sect;4.1 an incremental read cannot observe hard deletes, so the key-sweep is
 * a separate concern: the engine passes {@code incremental} to the callback, which runs the existing
 * {@code SoftDeleteSweeper} in FULL mode only. The engine never claims {@code modified}-filtering detects
 * deletions.
 *
 * <p><b>Watermark safety.</b> The stored value is {@code max(modified_at)} actually loaded — the
 * authoritative IIQ source change time, never processing time — and advances only after a run with zero
 * row-level failures ({@link WatermarkService#shouldAdvance}). A failed/partial run leaves the watermark,
 * so the next incremental re-scans the unprocessed window. FULL runs also advance it, so the first
 * {@code --incremental} always has a baseline cursor. The key is namespaced {@code native:<table>} so it
 * never collides with the REST watermark for the same table.
 */
public final class NativeCssExtraction {

    /** The entity-specific source query + persistence the engine drives. */
    @FunctionalInterface
    public interface Loader {
        /**
         * @param modifiedAfterIso the incremental bound (ISO-8601 UTC), or {@code null} for a full scan
         * @param incremental      {@code true} in INCREMENTAL mode — the callback must then run its
         *                         deletion sweep in FULL mode only (i.e. sweep when {@code !incremental})
         * @return the row counts for the run
         */
        Outcome load(String modifiedAfterIso, boolean incremental) throws SQLException;
    }

    /** Row counts from one native import, for the run ledger. */
    public record Outcome(int extracted, int inserted, int updated, int failed) {
    }

    private NativeCssExtraction() {
    }

    /**
     * Runs one native CSS extraction for {@code table} under {@code mode}: computes the incremental
     * bound (incremental only), invokes the entity {@code loader}, records the run, and advances the
     * watermark on success. Returns the loader's {@link Outcome}.
     */
    public static Outcome run(Connection conn, String schema, String table,
                              ExtractionMode mode, Loader loader) throws SQLException {
        boolean incremental = mode.isIncremental();
        String boundIso = incremental ? computeBoundIso(conn, schema, table, mode) : null;
        if (!incremental) {
            System.out.println("Full mode [native:" + table + "]: full scan (watermark not applied; "
                    + "advances to max(modified) so a later --incremental has a baseline)");
        }
        Outcome outcome = loader.load(boundIso, incremental);
        RunLedger.record(table, outcome.extracted(), outcome.inserted(), outcome.updated(), outcome.failed());
        advanceWatermark(conn, schema, table, outcome.failed());
        return outcome;
    }

    /** Native watermark key for a table, namespaced so it never collides with the REST watermark. */
    public static String watermarkEntity(String table) {
        return "native:" + table;
    }

    /** The effective server-side bound (ISO-8601) for incremental mode, or {@code null} (full scan). */
    private static String computeBoundIso(Connection conn, String schema, String table, ExtractionMode mode)
            throws SQLException {
        Instant prior = new WatermarkService(schema).readWatermark(conn, watermarkEntity(table));
        Instant boundary = IncrementalFilter.effectiveWatermark(prior, mode, IncrementalConfig.overlapWindow());
        System.out.println("Incremental mode [native:" + table + "]: watermark(modified)="
                + (prior == null ? "none (first run -> full scan)" : prior)
                + "  overlap=" + IncrementalConfig.overlapWindow().toMinutes() + "m"
                + "  server-side bound=" + (boundary == null ? "n/a (full scan)" : boundary));
        return boundary == null ? null : boundary.toString();
    }

    /** max(modified_at) in a native table = the authoritative high-water IIQ change time captured. */
    private static Instant maxModified(Connection conn, String schema, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT max(modified_at) FROM " + schema + "." + table)) {
            if (rs.next()) {
                OffsetDateTime odt = rs.getObject(1, OffsetDateTime.class);
                return odt == null ? null : odt.toInstant();
            }
        }
        return null;
    }

    /** Advances the watermark to max(modified_at) iff the run had zero row-level failures. */
    private static void advanceWatermark(Connection conn, String schema, String table, int failed)
            throws SQLException {
        Instant newWatermark = maxModified(conn, schema, table);
        boolean advanced = new WatermarkService(schema).advanceIfSuccessful(
                conn, watermarkEntity(table), "modified", newWatermark, failed, RunLedger.currentRunId());
        if (advanced) {
            System.out.println("Watermark advanced [native:" + table + "] -> " + newWatermark);
        } else if (failed > 0) {
            System.out.println("Watermark NOT advanced [native:" + table + "] (row-level failures present; "
                    + "next incremental run re-scans from the unchanged watermark)");
        }
    }
}
