package com.keyforge.iiq.lineage;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC access for the shared {@code kf_record_lineage} sidecar. Read-only against the domain
 * tables (a single INSERT…SELECT per table); the only table it creates/writes is its own. Never
 * alters or drops any existing table. Same conventions as the other repositories.
 */
public final class RecordLineageRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    private final String schema;
    private final String targetTable;

    public RecordLineageRepository() {
        this(DEFAULT_SCHEMA);
    }

    public RecordLineageRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + "." + RecordLineageSql.TABLE;
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute(RecordLineageSql.createTable(schema));
        }
    }

    public boolean tableExists(Connection conn, String table) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean columnExists(Connection conn, String table, String column) throws SQLException {
        if (column == null) {
            return false;
        }
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Backfills lineage rows for one domain table; returns the number of envelope rows written. */
    public int backfill(Connection conn, LineageSource s) throws SQLException {
        boolean hasSourceId = columnExists(conn, s.table(), "source_id");
        boolean hasNat = columnExists(conn, s.table(), s.natCol());
        boolean hasCreated = columnExists(conn, s.table(), s.createdCol());
        boolean hasModified = columnExists(conn, s.table(), s.modifiedCol());
        boolean hasExtracted = columnExists(conn, s.table(), "extracted_at");
        String sql = RecordLineageSql.backfill(schema, s, hasSourceId, hasNat, hasCreated, hasModified, hasExtracted);
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}
