package com.keyforge.iiq.nativeparquet;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Existing PostgreSQL row query, factored behind {@link ParquetRowSource}. */
final class PostgresParquetRowSource implements ParquetRowSource {
    private final Connection connection;
    private final String schema;

    PostgresParquetRowSource(Connection connection, String schema) {
        this.connection = connection;
        this.schema = schema;
    }

    @Override
    public List<Row> read(NativeParquetDatasets.Def d) throws SQLException {
        boolean hasSoftDelete = columnExists(d.sourceTable(), "is_deleted");
        boolean hasCreated = columnExists(d.sourceTable(), "created_at");
        boolean hasModified = columnExists(d.sourceTable(), "modified_at");
        String cols = String.join(", ", d.businessColumns());
        String sql = "SELECT " + d.pkColumn() + " AS __pk, " + cols
                + (hasCreated ? ", created_at AS __src_created" : "")
                + (hasModified ? ", modified_at AS __src_modified" : "")
                + " FROM " + schema + "." + d.sourceTable()
                + (hasSoftDelete ? " WHERE is_deleted = false" : "");
        List<Row> rows = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (String column : d.businessColumns()) {
                    values.put(column, rs.getString(column));
                }
                rows.add(new Row(rs.getString("__pk"), values,
                        hasCreated ? instant(rs, "__src_created") : null,
                        hasModified ? instant(rs, "__src_modified") : null));
            }
        }
        return rows;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
