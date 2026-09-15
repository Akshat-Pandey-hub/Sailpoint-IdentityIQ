package com.keyforge.iiq.config;

import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL connection settings, resolved from the environment using the same
 * philosophy as {@link AppConfig}: environment variables first, then JVM system
 * properties. Nothing is hardcoded and the password is never logged.
 *
 * <p>Settings: {@code PG_HOST}, {@code PG_PORT}, {@code PG_DATABASE},
 * {@code PG_USERNAME}, {@code PG_PASSWORD}, {@code PG_SCHEMA}. {@code PG_PORT} defaults
 * to 5432 and {@code PG_SCHEMA} defaults to {@value SchemaName#DEFAULT}.
 */
public final class PgConfig {

    public static final String KEY_HOST = "PG_HOST";
    public static final String KEY_PORT = "PG_PORT";
    public static final String KEY_DATABASE = "PG_DATABASE";
    public static final String KEY_USERNAME = "PG_USERNAME";
    public static final String KEY_PASSWORD = "PG_PASSWORD";
    public static final String KEY_SCHEMA = "PG_SCHEMA";

    private static final String DEFAULT_PORT = "5432";

    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private final String schema;

    public PgConfig(String host, String port, String database, String username, String password) {
        this(host, port, database, username, password, null);
    }

    public PgConfig(String host, String port, String database, String username, String password, String schema) {
        this.host = requireNonBlank(KEY_HOST, host);
        this.port = isBlank(port) ? DEFAULT_PORT : port.trim();
        this.database = requireNonBlank(KEY_DATABASE, database);
        this.username = requireNonBlank(KEY_USERNAME, username);
        this.password = requireNonBlank(KEY_PASSWORD, password);
        // PG_SCHEMA is optional; default to migration_test. Validated as a SQL identifier
        // because a schema name is concatenated into SQL, never bound as a parameter.
        this.schema = SchemaName.validate(isBlank(schema) ? SchemaName.DEFAULT : schema);
    }

    /**
     * Loads configuration from the environment.
     *
     * @throws ConfigException listing every missing required setting
     */
    public static PgConfig load() {
        String host = resolve(KEY_HOST);
        String port = resolve(KEY_PORT);
        String database = resolve(KEY_DATABASE);
        String username = resolve(KEY_USERNAME);
        String password = resolve(KEY_PASSWORD);
        String schema = resolve(KEY_SCHEMA);

        List<String> missing = new ArrayList<>();
        if (isBlank(host)) {
            missing.add(KEY_HOST);
        }
        if (isBlank(database)) {
            missing.add(KEY_DATABASE);
        }
        if (isBlank(username)) {
            missing.add(KEY_USERNAME);
        }
        if (isBlank(password)) {
            missing.add(KEY_PASSWORD);
        }
        if (!missing.isEmpty()) {
            throw new ConfigException(
                    "Missing required PostgreSQL configuration: " + String.join(", ", missing)
                    + ". Set these as environment variables (e.g. export " + KEY_HOST
                    + "=localhost) or JVM system properties (-D" + KEY_HOST + "=localhost). "
                    + KEY_PORT + " is optional (defaults to " + DEFAULT_PORT + "). "
                    + KEY_SCHEMA + " is optional (defaults to " + SchemaName.DEFAULT + ").");
        }

        return new PgConfig(host, port, database, username, password, schema);
    }

    /** e.g. {@code jdbc:postgresql://localhost:5432/my_local_db} (no credentials). */
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** The validated target schema for all migration tables (default {@value SchemaName#DEFAULT}). */
    public String getSchema() {
        return schema;
    }

    private static String resolve(String key) {
        String fromEnv = System.getenv(key);
        if (!isBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromSysProp = System.getProperty(key);
        if (!isBlank(fromSysProp)) {
            return fromSysProp.trim();
        }
        return null;
    }

    private static String requireNonBlank(String key, String value) {
        if (isBlank(value)) {
            throw new ConfigException("Missing required PostgreSQL configuration: " + key);
        }
        return value.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Deliberately excludes the password. */
    @Override
    public String toString() {
        return "PgConfig{url=" + getJdbcUrl() + ", schema=" + schema + ", username=" + username + ", password=***}";
    }
}
