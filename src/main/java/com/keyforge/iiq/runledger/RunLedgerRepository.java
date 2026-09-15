package com.keyforge.iiq.runledger;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/**
 * Plain-JDBC data access for the {@code kf_extraction_run} ledger table. Explicit DDL inside
 * {@code PG_SCHEMA}; one INSERT per run (the {@code extraction_run_id} is a fresh UUID each run, so
 * no conflict). Self-creating, like the other repositories.
 */
public class RunLedgerRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    private final String schema;
    private final String table;

    public RunLedgerRepository() {
        this(DEFAULT_SCHEMA);
    }

    public RunLedgerRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_extraction_run";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return table;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "extraction_run_id uuid PRIMARY KEY, "
                    + "command text NOT NULL, "
                    + "source_interface text, "
                    + "status text NOT NULL, "
                    + "started_at timestamptz NOT NULL, "
                    + "ended_at timestamptz, "
                    + "duration_ms bigint, "
                    + "window_start timestamptz, "
                    + "window_end timestamptz, "
                    + "extracted integer, "
                    + "inserted integer, "
                    + "updated integer, "
                    + "failed integer, "
                    + "entity_counts jsonb, "
                    + "error_message text, "
                    + "recorded_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public void insert(Connection conn, ExtractionRun r) throws SQLException {
        String sql = "INSERT INTO " + table + " (extraction_run_id, command, source_interface, status, "
                + "started_at, ended_at, duration_ms, window_start, window_end, extracted, inserted, updated, "
                + "failed, entity_counts, error_message) "
                + "VALUES (?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) "
                + "ON CONFLICT (extraction_run_id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.extractionRunId());
            ps.setString(i++, r.command());
            ps.setString(i++, r.sourceInterface());
            ps.setString(i++, r.status());
            setTs(ps, i++, r.startedAt());
            setTs(ps, i++, r.endedAt());
            setLong(ps, i++, r.durationMs());
            setTs(ps, i++, r.windowStart());
            setTs(ps, i++, r.windowEnd());
            setInt(ps, i++, r.extracted());
            setInt(ps, i++, r.inserted());
            setInt(ps, i++, r.updated());
            setInt(ps, i++, r.failed());
            ps.setString(i++, r.entityCountsJson());
            ps.setString(i++, r.errorMessage());
            ps.executeUpdate();
        }
    }

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setTimestamp(i, Timestamp.from(v));
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }
}
