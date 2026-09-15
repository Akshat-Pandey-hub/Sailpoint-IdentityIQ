package com.keyforge.iiq.incremental;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Plain-JDBC access for {@code kf_extraction_watermark}: one row per entity, holding the newest
 * source-side change time captured so far. Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert
 * on {@code entity}. Timestamps are bound as UTC {@link OffsetDateTime} (true instant into
 * {@code timestamptz}) and read back as {@link OffsetDateTime} so the stored instant is preserved
 * regardless of the JDBC session timezone.
 */
public class WatermarkRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    private final String schema;
    private final String targetTable;

    public WatermarkRepository() {
        this(DEFAULT_SCHEMA);
    }

    public WatermarkRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_extraction_watermark";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                + "entity text PRIMARY KEY, "
                + "watermark_field text, "
                + "source_watermark timestamptz, "
                + "last_run_id uuid, "
                + "updated_at timestamptz NOT NULL DEFAULT now())";
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute(createTableSql());
        }
    }

    /** The stored source watermark for an entity, or {@code null} when the entity has never run. */
    public Instant read(Connection conn, String entity) throws SQLException {
        String sql = "SELECT source_watermark FROM " + targetTable + " WHERE entity = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OffsetDateTime odt = rs.getObject(1, OffsetDateTime.class);
                return odt == null ? null : odt.toInstant();
            }
        }
    }

    /** Full stored watermark row for an entity, or {@code null} when never run. */
    public ExtractionWatermark readRow(Connection conn, String entity) throws SQLException {
        String sql = "SELECT entity, watermark_field, source_watermark, last_run_id FROM "
                + targetTable + " WHERE entity = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OffsetDateTime odt = rs.getObject(3, OffsetDateTime.class);
                Object runId = rs.getObject(4);
                return new ExtractionWatermark(rs.getString(1), rs.getString(2),
                        odt == null ? null : odt.toInstant(), runId == null ? null : runId.toString());
            }
        }
    }

    /** Idempotently sets/advances the watermark for an entity to {@code sourceWatermark}. */
    public void upsert(Connection conn, String entity, String watermarkField,
                       Instant sourceWatermark, String lastRunId) throws SQLException {
        String sql = "INSERT INTO " + targetTable
                + " (entity, watermark_field, source_watermark, last_run_id, updated_at) "
                + "VALUES (?,?,?,?::uuid, now()) "
                + "ON CONFLICT (entity) DO UPDATE SET watermark_field = EXCLUDED.watermark_field, "
                + "source_watermark = EXCLUDED.source_watermark, last_run_id = EXCLUDED.last_run_id, "
                + "updated_at = now()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity);
            ps.setString(2, watermarkField);
            if (sourceWatermark == null) {
                ps.setNull(3, Types.TIMESTAMP);
            } else {
                ps.setObject(3, sourceWatermark.atOffset(ZoneOffset.UTC));
            }
            ps.setString(4, lastRunId);
            ps.executeUpdate();
        }
    }
}
