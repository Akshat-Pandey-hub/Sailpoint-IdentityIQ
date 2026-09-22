package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;

/**
 * Resolves the separate PostgreSQL schema for native-sourced data, {@code IIQ_NATIVE_SCHEMA}
 * (default {@value #DEFAULT}). Deliberately independent of {@code PG_SCHEMA} — the REST/SCIM baseline
 * ({@code iiq_migration_final} / {@code migration_test}) and the native data live in separate schemas
 * of the same database so they can be compared without either touching the other. Validated as a SQL
 * identifier because a schema name is concatenated into DDL/DML, never bound as a parameter.
 */
public final class NativeSchemaConfig {

    public static final String KEY_SCHEMA = "IIQ_NATIVE_SCHEMA";
    public static final String DEFAULT = "iiq_native";

    private NativeSchemaConfig() {
    }

    /** The validated native schema from env / system property, or {@value #DEFAULT}. */
    public static String resolve() {
        String v = System.getenv(KEY_SCHEMA);
        if (v == null || v.trim().isEmpty()) {
            v = System.getProperty(KEY_SCHEMA);
        }
        return SchemaName.validate(v == null || v.trim().isEmpty() ? DEFAULT : v.trim());
    }
}
