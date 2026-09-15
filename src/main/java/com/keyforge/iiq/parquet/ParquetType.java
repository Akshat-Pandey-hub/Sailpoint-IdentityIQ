package com.keyforge.iiq.parquet;

/**
 * Logical column types for a Parquet dataset, mapped to the DuckDB SQL type used both when writing
 * the Parquet file (via a typed staging table) and when querying it. Kept small and explicit so the
 * Parquet schema is stable for the Phase-2 REST layer.
 *
 * <ul>
 *   <li>{@link #UUID_STR} — a canonical UUID stored as text (portable, FK-friendly, REST-friendly).</li>
 *   <li>{@link #STRING} / {@link #JSON} — text; JSON keeps genuinely variable nested source structure.</li>
 *   <li>{@link #BOOL} / {@link #INT} / {@link #LONG} / {@link #DOUBLE} — scalars.</li>
 *   <li>{@link #TIMESTAMP} — an absolute instant written as UTC {@code TIMESTAMP}.</li>
 * </ul>
 */
public enum ParquetType {
    UUID_STR("VARCHAR"),
    STRING("VARCHAR"),
    JSON("VARCHAR"),
    BOOL("BOOLEAN"),
    INT("INTEGER"),
    LONG("BIGINT"),
    DOUBLE("DOUBLE"),
    TIMESTAMP("TIMESTAMP");

    private final String duckDbType;

    ParquetType(String duckDbType) {
        this.duckDbType = duckDbType;
    }

    public String duckDbType() {
        return duckDbType;
    }
}
