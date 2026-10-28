package com.keyforge.nativeiiq.resource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/** Database products explicitly supported by the bundled SQL report variants. */
enum NativeSqlReportDialect {
    POSTGRESQL,
    ORACLE;

    static NativeSqlReportDialect fromProductName(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException("IdentityIQ database product name is missing");
        }
        String normalized = productName.trim();
        if ("PostgreSQL".equalsIgnoreCase(normalized)) {
            return POSTGRESQL;
        }
        if ("Oracle".equalsIgnoreCase(normalized) || "Oracle Database".equalsIgnoreCase(normalized)) {
            return ORACLE;
        }
        throw new IllegalArgumentException("Unsupported IdentityIQ database product: " + normalized);
    }

    static NativeSqlReportDialect fromConnection(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("IdentityIQ database connection is missing");
        }
        DatabaseMetaData metadata = connection.getMetaData();
        if (metadata == null) {
            throw new IllegalStateException("IdentityIQ database metadata is unavailable");
        }
        return fromProductName(metadata.getDatabaseProductName());
    }
}
