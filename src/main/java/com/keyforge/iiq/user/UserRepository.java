package com.keyforge.iiq.user;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code usr} migration table. The table
 * is defined and created by THIS project (explicit DDL) inside the configured
 * {@code PG_SCHEMA} — it does NOT clone {@code public.usr} and inherits no ISPM
 * columns, defaults, or enums. Idempotent upsert keyed on {@code userid}.
 */
public class UserRepository {

    /** Default migration schema when none is configured. */
    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    /** Result of an upsert. */
    public enum UpsertOutcome {
        INSERTED,
        UPDATED
    }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public UserRepository() {
        this(DEFAULT_SCHEMA);
    }

    public UserRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "userid uuid PRIMARY KEY, "
                        + "username text, "
                        + "displayname text, "
                        + "formatted_name text, "
                        + "firstname text, "
                        + "lastname text, "
                        + "email text, "
                        + "emails jsonb, "
                        + "active boolean, "
                        + "department text, "
                        + "employee_id text, "
                        + "is_manager boolean, "
                        + "risk_score integer, "
                        + "last_refresh timestamptz, "
                        + "capabilities jsonb, "
                        + "account_refs jsonb, "
                        + "enterprise_attributes jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (userid, username, displayname, formatted_name, firstname, lastname, email, emails, "
                        + "active, department, employee_id, is_manager, risk_score, last_refresh, capabilities, "
                        + "account_refs, enterprise_attributes, created_at, modified_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, "
                        + "?::jsonb, ?, ?) "
                        + "ON CONFLICT (userid) DO UPDATE SET "
                        + "username = EXCLUDED.username, displayname = EXCLUDED.displayname, "
                        + "formatted_name = EXCLUDED.formatted_name, firstname = EXCLUDED.firstname, "
                        + "lastname = EXCLUDED.lastname, email = EXCLUDED.email, emails = EXCLUDED.emails, "
                        + "active = EXCLUDED.active, department = EXCLUDED.department, "
                        + "employee_id = EXCLUDED.employee_id, is_manager = EXCLUDED.is_manager, "
                        + "risk_score = EXCLUDED.risk_score, last_refresh = EXCLUDED.last_refresh, "
                        + "capabilities = EXCLUDED.capabilities, account_refs = EXCLUDED.account_refs, "
                        + "enterprise_attributes = EXCLUDED.enterprise_attributes, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, UserRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.userid());
            ps.setString(2, row.username());
            ps.setString(3, row.displayName());
            ps.setString(4, row.formattedName());
            ps.setString(5, row.firstName());
            ps.setString(6, row.lastName());
            ps.setString(7, row.email());
            ps.setString(8, row.emailsJson());
            setBool(ps, 9, row.active());
            ps.setString(10, row.department());
            ps.setString(11, row.employeeId());
            setBool(ps, 12, row.isManager());
            setInt(ps, 13, row.riskScore());
            setTimestamp(ps, 14, row.lastRefresh());
            ps.setString(15, row.capabilitiesJson());
            ps.setString(16, row.accountRefsJson());
            ps.setString(17, row.enterpriseAttributesJson());
            setTimestamp(ps, 18, row.createdAt());
            setTimestamp(ps, 19, row.modifiedAt());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
