package com.keyforge.iiq.countrecon;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.rest.ParquetFileResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for cross-pipeline count reconciliation. Reads PostgreSQL table counts (guarded) and
 * Parquet dataset counts (via DuckDB over the latest part file, reusing {@link ParquetFileResolver}),
 * and writes results to its own {@code kf_count_reconciliation} table. Read-only against every domain
 * store; never alters them.
 */
public final class CountReconciliationRepository {

    static {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("DuckDB JDBC driver not on the classpath", e);
        }
    }

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;
    public static final String TABLE = "kf_count_reconciliation";

    private final String schema;
    private final String targetTable;
    private final ParquetFileResolver resolver;

    public CountReconciliationRepository(String schema, ParquetConfig parquetConfig) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + "." + TABLE;
        this.resolver = new ParquetFileResolver(parquetConfig);
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                    + "id uuid PRIMARY KEY, run_id text, domain text, pg_table text, parquet_dataset text, "
                    + "pg_count bigint, parquet_count bigint, delta bigint, status text, note text, "
                    + "checked_at timestamptz)");
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

    /**
     * PostgreSQL row count, or {@code -1} when the table is absent. Tables carrying the soft-delete
     * flag are counted as ACTIVE rows only (soft-deleted rows excluded), so the count reflects current
     * state; tables without the flag are counted in full.
     */
    public long pgCount(Connection conn, String table) throws SQLException {
        if (!tableExists(conn, table)) {
            return -1;
        }
        String sql = columnExists(conn, table, "is_deleted")
                ? CountReconciliation.pgActiveCountSql(schema, table)
                : CountReconciliation.pgCountSql(schema, table);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
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

    /** Parquet row count from the latest part file, or {@code -1} when no part file exists. */
    public long parquetCount(String dataset) throws SQLException {
        Optional<Path> file = resolver.resolve(dataset, null);
        if (file.isEmpty()) {
            return -1;
        }
        String path = file.get().toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        try (Connection duck = DriverManager.getConnection("jdbc:duckdb:");
             Statement st = duck.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM read_parquet('" + path + "')")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public void upsert(Connection conn, String runId, CountPair pair, long pgCount, long parquetCount,
                       String status, Instant checkedAt) throws SQLException {
        UUID id = UUID.nameUUIDFromBytes((runId + "|" + pair.domain()).getBytes(StandardCharsets.UTF_8));
        Long delta = (pgCount >= 0 && parquetCount >= 0) ? (pgCount - parquetCount) : null;
        String sql = "INSERT INTO " + targetTable + " (id, run_id, domain, pg_table, parquet_dataset, "
                + "pg_count, parquet_count, delta, status, note, checked_at) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (id) DO UPDATE SET run_id=EXCLUDED.run_id, pg_count=EXCLUDED.pg_count, "
                + "parquet_count=EXCLUDED.parquet_count, delta=EXCLUDED.delta, status=EXCLUDED.status, "
                + "note=EXCLUDED.note, checked_at=EXCLUDED.checked_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, runId);
            ps.setString(3, pair.domain());
            ps.setString(4, pair.pgTable());
            ps.setString(5, pair.parquetDataset());
            setLongOrNull(ps, 6, pgCount);
            setLongOrNull(ps, 7, parquetCount);
            if (delta == null) {
                ps.setNull(8, java.sql.Types.BIGINT);
            } else {
                ps.setLong(8, delta);
            }
            ps.setString(9, status);
            ps.setString(10, note(status, pgCount, parquetCount));
            ps.setTimestamp(11, Timestamp.from(checkedAt));
            ps.executeUpdate();
        }
    }

    private static void setLongOrNull(PreparedStatement ps, int idx, long v) throws SQLException {
        if (v < 0) {
            ps.setNull(idx, java.sql.Types.BIGINT);
        } else {
            ps.setLong(idx, v);
        }
    }

    private static String note(String status, long pg, long pq) {
        return switch (status) {
            case CountReconciliation.MISMATCH -> "pg=" + pg + " parquet=" + pq + " delta=" + (pg - pq);
            case CountReconciliation.PG_ONLY -> "no Parquet dataset extracted yet";
            case CountReconciliation.PARQUET_ONLY -> "no PostgreSQL table present";
            case CountReconciliation.BOTH_ABSENT -> "neither pipeline has this domain yet";
            default -> null;
        };
    }
}
