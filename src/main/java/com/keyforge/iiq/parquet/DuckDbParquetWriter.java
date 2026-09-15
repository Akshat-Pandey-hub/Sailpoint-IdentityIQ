package com.keyforge.iiq.parquet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Writes a Parquet file from in-memory rows using an embedded DuckDB (no Hadoop, no PostgreSQL).
 * Rows are staged into a typed temp table (schema = {@link DatasetSpec#allColumns()}) and then
 * {@code COPY ... TO '<file>' (FORMAT PARQUET)} produces the file. An empty row list still writes a
 * valid Parquet file carrying the full schema (zero rows). Timestamps are written as UTC wall-clock
 * {@code TIMESTAMP} values, consistent with the rest of the project.
 */
public final class DuckDbParquetWriter {

    static {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("DuckDB JDBC driver not on the classpath", e);
        }
    }

    private DuckDbParquetWriter() {
    }

    /** Writes {@code rows} to {@code outFile} as Parquet; returns the number of rows written. */
    public static long write(DatasetSpec spec, List<Map<String, Object>> rows, Path outFile) throws SQLException {
        try {
            Files.createDirectories(outFile.toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Cannot create output directory for " + outFile, e);
        }
        List<Column> cols = spec.allColumns();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            createStage(conn, cols);
            long written = insertRows(conn, cols, rows == null ? List.of() : rows);
            copyToParquet(conn, outFile);
            return written;
        }
    }

    private static void createStage(Connection conn, List<Column> cols) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE stage (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                ddl.append(", ");
            }
            ddl.append('"').append(cols.get(i).name()).append("\" ").append(cols.get(i).type().duckDbType());
        }
        ddl.append(')');
        try (Statement st = conn.createStatement()) {
            st.execute(ddl.toString());
        }
    }

    private static long insertRows(Connection conn, List<Column> cols, List<Map<String, Object>> rows)
            throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("INSERT INTO stage VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(')');
        long n = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < cols.size(); i++) {
                    bind(ps, i + 1, cols.get(i).type(), row.get(cols.get(i).name()));
                }
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    private static void bind(PreparedStatement ps, int idx, ParquetType type, Object v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, sqlType(type));
            return;
        }
        switch (type) {
            case BOOL -> ps.setBoolean(idx, (Boolean) v);
            case INT -> ps.setInt(idx, ((Number) v).intValue());
            case LONG -> ps.setLong(idx, ((Number) v).longValue());
            case DOUBLE -> ps.setDouble(idx, ((Number) v).doubleValue());
            case TIMESTAMP -> ps.setObject(idx, toUtcLocal(v));
            default -> ps.setString(idx, v.toString());   // UUID_STR, STRING, JSON
        }
    }

    /** Accepts Instant or LocalDateTime; stores the UTC wall-clock LocalDateTime. */
    private static LocalDateTime toUtcLocal(Object v) {
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof Instant inst) {
            return inst.atOffset(ZoneOffset.UTC).toLocalDateTime();
        }
        throw new IllegalArgumentException("TIMESTAMP column expects Instant/LocalDateTime, got " + v.getClass());
    }

    private static int sqlType(ParquetType type) {
        return switch (type) {
            case BOOL -> Types.BOOLEAN;
            case INT -> Types.INTEGER;
            case LONG -> Types.BIGINT;
            case DOUBLE -> Types.DOUBLE;
            case TIMESTAMP -> Types.TIMESTAMP;
            default -> Types.VARCHAR;
        };
    }

    private static void copyToParquet(Connection conn, Path outFile) throws SQLException {
        String path = outFile.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        try (Statement st = conn.createStatement()) {
            st.execute("COPY (SELECT * FROM stage) TO '" + path + "' (FORMAT PARQUET)");
        }
    }
}
