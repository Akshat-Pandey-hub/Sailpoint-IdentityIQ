package com.keyforge.iiq.config;

/**
 * Validation for the configurable PostgreSQL target schema ({@code PG_SCHEMA}).
 *
 * <p>A schema name is a SQL <em>identifier</em>, not a query value, so it cannot be
 * bound as a {@code ?} parameter — it is concatenated into DDL/DML. To keep that safe,
 * every schema string passes through {@link #validate(String)} before it reaches any
 * SQL, which restricts it to a plain unquoted PostgreSQL identifier
 * ({@code [A-Za-z_][A-Za-z0-9_]*}, max 63 chars). Anything else is rejected, so no
 * injection or malformed identifier can flow into a statement.
 */
public final class SchemaName {

    /** The default target schema for local development when {@code PG_SCHEMA} is unset. */
    public static final String DEFAULT = "migration_test";

    private static final int MAX_IDENTIFIER_LENGTH = 63; // PostgreSQL NAMEDATALEN - 1

    private SchemaName() {
    }

    /**
     * Validates and normalises a schema identifier for safe use in SQL.
     *
     * @return the trimmed schema name (unchanged casing)
     * @throws ConfigException if the value is blank or not a plain SQL identifier
     */
    public static String validate(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            throw new ConfigException("PG_SCHEMA must not be blank.");
        }
        String s = schema.trim();
        if (!s.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ConfigException("PG_SCHEMA '" + s + "' is not a valid PostgreSQL identifier "
                    + "(use letters, digits and underscores; it must not start with a digit).");
        }
        if (s.length() > MAX_IDENTIFIER_LENGTH) {
            throw new ConfigException("PG_SCHEMA '" + s + "' exceeds PostgreSQL's "
                    + MAX_IDENTIFIER_LENGTH + "-character identifier limit.");
        }
        return s;
    }
}
