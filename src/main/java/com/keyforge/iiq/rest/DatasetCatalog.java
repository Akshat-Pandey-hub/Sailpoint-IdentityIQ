package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.Column;
import com.keyforge.iiq.parquet.DatasetSpec;
import com.keyforge.iiq.parquet.ParquetDatasets;
import com.keyforge.iiq.parquet.ParquetType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of the known Parquet datasets and their schemas, sourced directly from the existing
 * {@link ParquetDatasets} registry (schemas are not duplicated). Used by the REST layer to validate
 * dataset names, field names, filter/sort columns, and operator/type compatibility before any SQL is
 * built.
 */
public final class DatasetCatalog {

    private final Map<String, Map<String, ParquetType>> columnsByDataset = new LinkedHashMap<>();

    public DatasetCatalog() {
        ParquetDatasets datasets = new ParquetDatasets();
        for (String name : datasets.names()) {
            DatasetSpec spec = datasets.get(name).spec();
            Map<String, ParquetType> cols = new LinkedHashMap<>();
            for (Column c : spec.allColumns()) {
                cols.put(c.name(), c.type());
            }
            columnsByDataset.put(name, cols);
        }
    }

    public List<String> datasets() {
        return new ArrayList<>(columnsByDataset.keySet());
    }

    public boolean hasDataset(String dataset) {
        return columnsByDataset.containsKey(dataset);
    }

    /** Ordered column name → type map for a dataset. */
    public Map<String, ParquetType> columns(String dataset) {
        Map<String, ParquetType> cols = columnsByDataset.get(dataset);
        if (cols == null) {
            throw ApiException.notFound("Dataset '" + dataset + "' is not supported");
        }
        return cols;
    }

    public List<String> columnNames(String dataset) {
        return new ArrayList<>(columns(dataset).keySet());
    }

    /** Returns the column's type, or 400 if the column is not in the dataset schema. */
    public ParquetType requireColumn(String dataset, String column) {
        ParquetType t = columns(dataset).get(column);
        if (t == null) {
            throw ApiException.badRequest("Unknown field '" + column + "' for dataset '" + dataset
                    + "'. Valid fields: " + columnNames(dataset));
        }
        return t;
    }
}
