package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.ParquetType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses HTTP query parameters into a validated {@link QuerySpec}. Every field, filter column, sort
 * column, operator, and pagination value is checked against the dataset's real schema (via
 * {@link DatasetCatalog}) before it can reach SQL. Unknown datasets/fields/operators or nonsensical
 * operator/type combinations raise {@link ApiException} (400/404) with a helpful message.
 *
 * <p>Filter syntax:
 * <ul>
 *   <li>{@code ?field=value} — equality (field must be a schema column).</li>
 *   <li>{@code ?filter.field.op=value} — operator form (op: eq, ne, gt, gte, lt, lte, contains,
 *       startsWith, in). {@code ?filter.field=value} is equality.</li>
 *   <li>{@code in} takes a comma-separated value list.</li>
 * </ul>
 * Reserved (non-filter) params: {@code fields, sort, order, limit, offset, run}.
 */
public final class QueryParser {

    private static final Set<String> RESERVED = Set.of("fields", "sort", "order", "limit", "offset", "run");

    private final DatasetCatalog catalog;
    private final int defaultLimit;
    private final int maxLimit;

    public QueryParser(DatasetCatalog catalog, int defaultLimit, int maxLimit) {
        this.catalog = catalog;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    public QuerySpec parse(String dataset, Map<String, List<String>> params) {
        if (!catalog.hasDataset(dataset)) {
            throw ApiException.notFound("Dataset '" + dataset + "' is not supported");
        }

        List<String> fields = new ArrayList<>();
        if (params.containsKey("fields")) {
            for (String f : first(params, "fields").split(",")) {
                String col = f.trim();
                if (!col.isEmpty()) {
                    catalog.requireColumn(dataset, col); // 400 if unknown
                    fields.add(col);
                }
            }
        }

        String sortColumn = null;
        boolean descending = false;
        if (params.containsKey("sort")) {
            sortColumn = first(params, "sort").trim();
            catalog.requireColumn(dataset, sortColumn);
            String order = params.containsKey("order") ? first(params, "order").trim().toLowerCase() : "asc";
            if (order.equals("desc")) {
                descending = true;
            } else if (!order.equals("asc")) {
                throw ApiException.badRequest("order must be 'asc' or 'desc' but was: " + order);
            }
        }

        int limit = clampLimit(params.containsKey("limit") ? parseInt("limit", first(params, "limit")) : defaultLimit);
        int offset = params.containsKey("offset") ? parseInt("offset", first(params, "offset")) : 0;
        if (offset < 0) {
            throw ApiException.badRequest("offset must be >= 0");
        }
        String run = params.containsKey("run") ? validateRun(first(params, "run")) : null;

        List<QuerySpec.Filter> filters = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            String key = e.getKey();
            if (RESERVED.contains(key)) {
                continue;
            }
            String value = e.getValue().isEmpty() ? "" : e.getValue().get(0);
            if (key.startsWith("filter.")) {
                filters.add(parseFilterParam(dataset, key.substring("filter.".length()), value));
            } else {
                // simple equality form: the key must be a schema column
                ParquetType type = catalog.requireColumn(dataset, key);
                filters.add(new QuerySpec.Filter(key, type, Operator.eq, List.of(value)));
            }
        }

        return new QuerySpec(dataset, fields, filters, sortColumn, descending, limit, offset, run);
    }

    private QuerySpec.Filter parseFilterParam(String dataset, String spec, String value) {
        String[] parts = spec.split("\\.");
        if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
            throw ApiException.badRequest("Invalid filter '" + spec + "'. Use filter.<field> or filter.<field>.<op>");
        }
        String column = parts[0];
        ParquetType type = catalog.requireColumn(dataset, column);
        Operator op = parts.length == 2 ? Operator.from(parts[1]) : Operator.eq;
        if (!op.appliesTo(type)) {
            throw ApiException.badRequest("Operator '" + op + "' is not valid for field '" + column
                    + "' of type " + type + " in dataset '" + dataset + "'");
        }
        List<String> values = op == Operator.in ? splitCsv(value) : List.of(value);
        if (values.isEmpty()) {
            throw ApiException.badRequest("Filter '" + column + "' requires a value");
        }
        return new QuerySpec.Filter(column, type, op, values);
    }

    private static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        for (String v : value.split(",")) {
            if (!v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }

    private int clampLimit(int limit) {
        if (limit < 1) {
            throw ApiException.badRequest("limit must be >= 1");
        }
        return Math.min(limit, maxLimit);
    }

    private static int parseInt(String name, String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " must be an integer but was: " + v);
        }
    }

    /** run ids are UUIDs; restrict to a safe charset so the value can never escape the file name. */
    private static String validateRun(String run) {
        String r = run.trim();
        if (!r.matches("[0-9a-fA-F-]{1,64}")) {
            throw ApiException.badRequest("Invalid run id");
        }
        return r;
    }

    private static String first(Map<String, List<String>> params, String key) {
        List<String> v = params.get(key);
        return v == null || v.isEmpty() ? "" : v.get(0);
    }
}
