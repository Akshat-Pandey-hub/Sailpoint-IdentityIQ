package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.ParquetType;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a validated {@link QuerySpec} against a single Parquet file using DuckDB. Column names come
 * only from the validated schema (quoted identifiers); filter <b>values</b> are always bound as
 * parameters (with an explicit CAST to the column's type), so no caller input reaches SQL as code.
 * Benefits from DuckDB's Parquet column pruning + predicate pushdown; never loads the whole file into
 * Java memory.
 */
public final class ParquetQueryService {

    static {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("DuckDB JDBC driver not on the classpath", e);
        }
    }

    private final DatasetCatalog catalog;

    public ParquetQueryService(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    public record QueryResult(String dataset, List<String> columns, List<Map<String, Object>> rows,
                              int returned, long total, int limit, int offset) {
    }

    public QueryResult query(QuerySpec q, Path parquetFile) {
        List<String> selected = q.fields().isEmpty() ? catalog.columnNames(q.dataset()) : q.fields();
        Map<String, ParquetType> schema = catalog.columns(q.dataset());
        String from = "read_parquet('" + parquetFile.toAbsolutePath().toString()
                .replace('\\', '/').replace("'", "''") + "')";

        List<Object> whereParams = new ArrayList<>();
        String where = buildWhere(q.filters(), whereParams);

        String projection = quotedList(selected);
        String orderBy = q.sortColumn() != null
                ? " ORDER BY \"" + q.sortColumn() + "\" " + (q.descending() ? "DESC" : "ASC")
                : " ORDER BY \"" + selected.get(0) + "\" ASC"; // deterministic default
        String dataSql = "SELECT " + projection + " FROM " + from + where + orderBy + " LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM " + from + where;

        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            long total;
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                bindAll(ps, whereParams, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                int idx = bindAll(ps, whereParams, 1);
                ps.setInt(idx++, q.limit());
                ps.setInt(idx, q.offset());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 0; i < selected.size(); i++) {
                            row.put(selected.get(i), readValue(rs, i + 1, schema.get(selected.get(i))));
                        }
                        rows.add(row);
                    }
                }
            }
            return new QueryResult(q.dataset(), selected, rows, rows.size(), total, q.limit(), q.offset());
        } catch (SQLException e) {
            // A bad filter value (e.g. non-numeric for a numeric column) surfaces here as a cast error.
            throw ApiException.badRequest("Query could not be executed (check filter values/types): " + e.getMessage());
        }
    }

    // ---- SQL building (identifiers validated upstream; values always bound) ----

    private String buildWhere(List<QuerySpec.Filter> filters, List<Object> outParams) {
        if (filters.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        for (QuerySpec.Filter f : filters) {
            String col = "\"" + f.column() + "\"";
            switch (f.op()) {
                case eq -> { clauses.add(col + " = " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case ne -> { clauses.add(col + " <> " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case gt -> { clauses.add(col + " > " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case gte -> { clauses.add(col + " >= " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case lt -> { clauses.add(col + " < " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case lte -> { clauses.add(col + " <= " + castParam(f.type())); outParams.add(f.values().get(0)); }
                case contains -> { clauses.add(col + " LIKE '%' || ? || '%'"); outParams.add(f.values().get(0)); }
                case startsWith -> { clauses.add(col + " LIKE ? || '%'"); outParams.add(f.values().get(0)); }
                case in -> {
                    List<String> ph = new ArrayList<>();
                    for (String v : f.values()) {
                        ph.add(castParam(f.type()));
                        outParams.add(v);
                    }
                    clauses.add(col + " IN (" + String.join(", ", ph) + ")");
                }
                default -> throw ApiException.badRequest("Unsupported operator: " + f.op());
            }
        }
        return " WHERE " + String.join(" AND ", clauses);
    }

    /** A bound placeholder cast to the column's type (value bound as text; DuckDB performs the cast). */
    private static String castParam(ParquetType type) {
        return switch (type) {
            case INT -> "CAST(? AS INTEGER)";
            case LONG -> "CAST(? AS BIGINT)";
            case DOUBLE -> "CAST(? AS DOUBLE)";
            case BOOL -> "CAST(? AS BOOLEAN)";
            case TIMESTAMP -> "CAST(? AS TIMESTAMP)";
            default -> "?"; // STRING / UUID_STR / JSON
        };
    }

    private static int bindAll(PreparedStatement ps, List<Object> params, int start) throws SQLException {
        int i = start;
        for (Object p : params) {
            ps.setString(i++, normalize(p));
        }
        return i;
    }

    /** Bound as text; trim a trailing 'Z' so ISO UTC timestamps cast cleanly to tz-naive TIMESTAMP. */
    private static String normalize(Object p) {
        if (p == null) {
            return null;
        }
        String s = p.toString();
        if (s.endsWith("Z") && s.contains("T")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String quotedList(List<String> cols) {
        List<String> q = new ArrayList<>();
        for (String c : cols) {
            q.add("\"" + c + "\"");
        }
        return String.join(", ", q);
    }

    private static Object readValue(ResultSet rs, int idx, ParquetType type) throws SQLException {
        switch (type) {
            case BOOL -> {
                boolean b = rs.getBoolean(idx);
                return rs.wasNull() ? null : b;
            }
            case INT -> {
                int v = rs.getInt(idx);
                return rs.wasNull() ? null : v;
            }
            case LONG -> {
                long v = rs.getLong(idx);
                return rs.wasNull() ? null : v;
            }
            case DOUBLE -> {
                double v = rs.getDouble(idx);
                return rs.wasNull() ? null : v;
            }
            case TIMESTAMP -> {
                LocalDateTime ldt = rs.getObject(idx, LocalDateTime.class);
                return ldt == null ? null
                        : ldt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            default -> {
                return rs.getString(idx);
            }
        }
    }
}
