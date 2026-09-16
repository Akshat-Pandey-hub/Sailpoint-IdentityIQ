package com.keyforge.iiq.rest;

/**
 * Runtime configuration for the REST query service. Env-first (env, then JVM system property), same
 * convention as the rest of the tool. The Parquet location comes from
 * {@link com.keyforge.iiq.parquet.ParquetConfig} (env {@code PARQUET_OUT_DIR}); this class adds only
 * the HTTP host/port. Nothing here connects to IdentityIQ or PostgreSQL.
 */
public final class RestConfig {

    public static final String KEY_HOST = "REST_HOST";
    public static final String KEY_PORT = "REST_PORT";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8100;

    /** Safe pagination bounds for every query. */
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 1000;

    private final String host;
    private final int port;

    public RestConfig(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static RestConfig load() {
        String host = resolve(KEY_HOST);
        String portStr = resolve(KEY_PORT);
        int port = DEFAULT_PORT;
        if (portStr != null) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                throw new com.keyforge.iiq.config.ConfigException(KEY_PORT + " must be an integer but was: " + portStr);
            }
        }
        return new RestConfig(host == null ? DEFAULT_HOST : host.trim(), port);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    private static String resolve(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            v = System.getProperty(key);
        }
        return v == null || v.isBlank() ? null : v;
    }
}
