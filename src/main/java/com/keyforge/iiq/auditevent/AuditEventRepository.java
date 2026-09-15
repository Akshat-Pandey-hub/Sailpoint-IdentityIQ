package com.keyforge.iiq.auditevent;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for {@code kf_audit_event}. Explicit DDL inside {@code PG_SCHEMA};
 * idempotent upsert on the {@code auditid} PK.
 *
 * <p>{@code created_at} is deliberately {@code timestamp} (WITHOUT time zone): the source
 * {@code created} value is a naive display string with no timezone and no seconds, so a
 * {@code timestamptz} would fabricate a zone. The verbatim source string is also kept in
 * {@code created_display}, so the mapping is lossless. No {@code action_normalized} column is
 * created — that taxonomy is not yet PDF-verified.
 */
public class AuditEventRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String table;

    public AuditEventRepository() {
        this(DEFAULT_SCHEMA);
    }

    public AuditEventRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_audit_event";
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "auditid uuid PRIMARY KEY, "
                    + "action text, "
                    + "source text, "
                    + "target text, "
                    + "created_display text, "
                    + "created_at timestamp, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, AuditEventRow r) throws SQLException {
        String sql = "INSERT INTO " + table + " (auditid, action, source, target, created_display, created_at) "
                + "VALUES (?::uuid,?,?,?,?,?) "
                + "ON CONFLICT (auditid) DO UPDATE SET action=EXCLUDED.action, source=EXCLUDED.source, "
                + "target=EXCLUDED.target, created_display=EXCLUDED.created_display, "
                + "created_at=EXCLUDED.created_at, extracted_at=now() "
                + "RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.auditid());
            ps.setString(2, r.action());
            ps.setString(3, r.source());
            ps.setString(4, r.target());
            ps.setString(5, r.createdDisplay());
            setTs(ps, 6, r.createdAt());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTs(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setObject(i, v);
    }
}
