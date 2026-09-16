package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.ParquetType;

import java.util.List;

/**
 * A validated query over one dataset: the selected fields (empty = all), the filters, optional sort,
 * and pagination. Everything here has already been checked against the dataset's real schema, so the
 * SQL builder can trust column names and operator/type compatibility.
 */
public record QuerySpec(
        String dataset,
        List<String> fields,
        List<Filter> filters,
        String sortColumn,
        boolean descending,
        int limit,
        int offset,
        String run) {

    /** One filter clause: a schema column, an operator valid for its type, and bound value(s). */
    public record Filter(String column, ParquetType type, Operator op, List<String> values) {
    }
}
