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

        Result(List<String> columns, List<Map<String, String>> rows, boolean truncated) {
            this.columns = columns;
            this.rows = rows;
            this.truncated = truncated;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    Result parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("SQL-report endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected SQL-report response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String type = error.path("type").asText("unknown");
            String message = error.path("message").asText("");
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + type
                    + (message.isEmpty() ? "" : ": " + message)
                    + (error.isTextual() ? ": " + error.asText() : ""));
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
            throw new NativeImportException("Unexpected SQL-report response (no 'rows' array) -- body: "
                    + snippet(json));
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
        return new Result(columns, rows, truncated);
    }

    private static String snippet(String json) {
        if (json == null || json.isEmpty()) {
            return "<empty>";
        }
        String s = json.strip().replaceAll("\\s+", " ");
        return s.length() <= 500 ? s : s.substring(0, 500) + "... (truncated)";
    }
}
