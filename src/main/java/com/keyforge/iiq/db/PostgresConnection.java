package com.keyforge.iiq.db;

import com.keyforge.iiq.config.PgConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens plain JDBC connections to PostgreSQL from a {@link PgConfig}. No pooling,
 * no framework — a single connection per command run is all this tool needs.
 */
public final class PostgresConnection {

    private PostgresConnection() {
    }

    /**
     * Opens a new connection. The caller owns it and must close it (try-with-resources).
     *
     * @throws SQLException if the connection cannot be established
     */
    public static Connection open(PgConfig config) throws SQLException {
        return DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }
}
