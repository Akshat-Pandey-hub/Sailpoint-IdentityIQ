package com.keyforge.iiq.accountentitlement;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_account_entitlement} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic {@code id}.
 * Separate from the existing {@code entitlementassignment} table, which is untouched.
 */
public class AccountEntitlementRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome {
        INSERTED,
        UPDATED
    }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public AccountEntitlementRepository() {
        this(DEFAULT_SCHEMA);
    }

    public AccountEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_account_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "account_id uuid, "
                        + "account_native_name text, "
                        + "application_id uuid, "
                        + "application_name text, "
                        + "entitlement_id uuid, "
                        + "entitlement_value text, "
                        + "entitlement_type text, "
                        + "source_attribute text, "
                        + "resolution_status text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (id, account_id, account_native_name, application_id, application_name, "
                        + "entitlement_id, entitlement_value, entitlement_type, source_attribute, resolution_status) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "account_id = EXCLUDED.account_id, account_native_name = EXCLUDED.account_native_name, "
                        + "application_id = EXCLUDED.application_id, application_name = EXCLUDED.application_name, "
                        + "entitlement_id = EXCLUDED.entitlement_id, entitlement_value = EXCLUDED.entitlement_value, "
                        + "entitlement_type = EXCLUDED.entitlement_type, source_attribute = EXCLUDED.source_attribute, "
                        + "resolution_status = EXCLUDED.resolution_status, extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, AccountEntitlementRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.id());
            ps.setString(2, row.accountId());
            ps.setString(3, row.accountNativeName());
            ps.setString(4, row.applicationId());
            ps.setString(5, row.applicationName());
            ps.setString(6, row.entitlementId());
            ps.setString(7, row.entitlementValue());
            ps.setString(8, row.entitlementType());
            ps.setString(9, row.sourceAttribute());
            ps.setString(10, row.resolutionStatus());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
