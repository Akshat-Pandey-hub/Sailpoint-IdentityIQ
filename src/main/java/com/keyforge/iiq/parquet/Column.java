package com.keyforge.iiq.parquet;

/** One column in a Parquet dataset schema. All columns are nullable (Parquet optional). */
public record Column(String name, ParquetType type) {

    public static Column of(String name, ParquetType type) {
        return new Column(name, type);
    }
}
