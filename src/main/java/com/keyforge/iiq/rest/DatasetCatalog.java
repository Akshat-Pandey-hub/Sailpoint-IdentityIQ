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

    /** The REST/SCIM catalog, sourced from the existing {@link ParquetDatasets} registry. */
    public DatasetCatalog() {
        ParquetDatasets datasets = new ParquetDatasets();
        for (String name : datasets.names()) {
            addSpec(datasets.get(name).spec());
        }
    }

    private DatasetCatalog(List<DatasetSpec> specs) {
        for (DatasetSpec spec : specs) {
            addSpec(spec);
        }
    }

    /**
     * Builds a catalog from an explicit list of dataset specs (e.g. the native-source datasets), so the
     * exact same validation + query engine can serve an isolated, separately-registered set of datasets
     * without touching the REST/SCIM registry.
     */
    public static DatasetCatalog forSpecs(List<DatasetSpec> specs) {
        return new DatasetCatalog(specs);
    }

    private void addSpec(DatasetSpec spec) {
        Map<String, ParquetType> cols = new LinkedHashMap<>();
        for (Column c : spec.allColumns()) {
            cols.put(c.name(), c.type());
        }
        columnsByDataset.put(spec.name(), cols);
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
