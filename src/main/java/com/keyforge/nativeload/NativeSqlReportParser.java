package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the SQL-report plugin envelope into {@link Result}. Fails loudly on a non-JSON body, a body
 * carrying a controlled {@code error} object, or a body without a {@code rows} array. Column order is
 * preserved from the {@code columns} array (the SQL aliases). A JSON {@code null} value is preserved as
 * a Java {@code null} (SQL NULL fidelity); all other values are kept verbatim as strings.
 */
final class NativeSqlReportParser {

    static final class Result {
        final List<String> columns;
        final List<Map<String, String>> rows;
        final boolean truncated;
        final boolean hasMore;
        final boolean pagingApplied;
        /** The IIQ database the plugin detected ("Oracle", "PostgreSQL"); null from an older plugin. */
        final String database;
        /** The bundled SQL file the plugin ran; null from an older plugin. */
        final String sqlFile;

        Result(List<String> columns, List<Map<String, String>> rows, boolean truncated,
               boolean hasMore, boolean pagingApplied) {
            this(columns, rows, truncated, hasMore, pagingApplied, null, null);
        }

        Result(List<String> columns, List<Map<String, String>> rows, boolean truncated,
               boolean hasMore, boolean pagingApplied, String database, String sqlFile) {
            this.columns = columns;
            this.rows = rows;
            this.truncated = truncated;
            this.hasMore = hasMore;
            this.pagingApplied = pagingApplied;
            this.database = database;
            this.sqlFile = sqlFile;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    Result parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("IIQ SQL-report endpoint returned an invalid response.", e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("IIQ SQL-report endpoint returned an invalid response.");
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String message = error.path("message").asText("");
            int status = root.path("status").asInt(500);
            if (status == 400 && !message.isEmpty()) {
                throw new IllegalArgumentException(message);
            }
            throw new NativeImportException("IIQ SQL report failed (HTTP " + status + ")"
                    + (message.isEmpty() ? "." : ": " + message));
        }

        List<String> columns = new ArrayList<String>();
        JsonNode cols = root.get("columns");
        if (cols != null && cols.isArray()) {
            for (JsonNode c : cols) {
                columns.add(c.asText());
            }
        }

        JsonNode rowsNode = root.get("rows");
        if (rowsNode == null || !rowsNode.isArray()) {
            throw new NativeImportException("IIQ SQL-report endpoint returned an invalid response.");
        }
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (JsonNode r : rowsNode) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            // If columns are known, iterate them (stable order); else iterate the row's own fields.
            if (!columns.isEmpty()) {
                for (String col : columns) {
                    JsonNode v = r.get(col);
                    row.put(col, (v == null || v.isNull()) ? null : v.asText());
                }
            } else {
                java.util.Iterator<String> it = r.fieldNames();
                while (it.hasNext()) {
                    String f = it.next();
                    JsonNode v = r.get(f);
                    row.put(f, (v == null || v.isNull()) ? null : v.asText());
                }
            }
            rows.add(row);
        }

        boolean truncated = root.path("truncated").asBoolean(false);
        boolean hasMore = root.path("hasMore").asBoolean(false);
        boolean pagingApplied = root.path("pagingApplied").asBoolean(false);
        return new Result(columns, rows, truncated, hasMore, pagingApplied,
                textOrNull(root.get("database")), textOrNull(root.get("sqlFile")));
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText("").trim();
        return text.isEmpty() ? null : text;
    }
}
