package com.keyforge.iiq.account;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Plain-JDBC data access for the project-owned {@code account} migration table.
 * Explicit DDL inside {@code PG_SCHEMA}; does NOT clone {@code public.account} and
 * inherits no ISPM columns/defaults/enums. Idempotent upsert keyed on {@code accountid}.
 */
public class AccountRepository {

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

    public AccountRepository() {
        this(DEFAULT_SCHEMA);
    }

    public AccountRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_account";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "accountid uuid PRIMARY KEY, "
                        + "userid uuid, "
                        + "identity_display_name text, "
                        + "instanceid uuid, "
                        + "application_display_name text, "
                        + "native_identity text, "
                        + "account_display_name text, "
                        + "active boolean, "
                        + "locked boolean, "
                        + "has_entitlements boolean, "
                        + "manually_correlated boolean, "
                        + "last_refresh timestamptz, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "attributes jsonb, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (accountid, userid, identity_display_name, instanceid, application_display_name, "
                        + "native_identity, account_display_name, active, locked, has_entitlements, "
                        + "manually_correlated, last_refresh, created_at, modified_at, attributes) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (accountid) DO UPDATE SET "
                        + "userid = EXCLUDED.userid, identity_display_name = EXCLUDED.identity_display_name, "
                        + "instanceid = EXCLUDED.instanceid, "
                        + "application_display_name = EXCLUDED.application_display_name, "
                        + "native_identity = EXCLUDED.native_identity, "
                        + "account_display_name = EXCLUDED.account_display_name, active = EXCLUDED.active, "
                        + "locked = EXCLUDED.locked, has_entitlements = EXCLUDED.has_entitlements, "
                        + "manually_correlated = EXCLUDED.manually_correlated, last_refresh = EXCLUDED.last_refresh, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "attributes = EXCLUDED.attributes, extracted_at = now() "
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

    /** Canonical userids present in {@code <schema>.kf_identity} (empty if the table is absent). */
    public Set<String> getExistingUserIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_identity", "userid");
    }

    /** Canonical instanceids present in {@code <schema>.applicationinstance} (empty if absent). */
    public Set<String> getExistingInstanceIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".applicationinstance", "instanceid");
    }

    public UpsertOutcome upsert(Connection conn, AccountRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.accountid());
            ps.setString(2, row.userid());
            ps.setString(3, row.identityDisplayName());
            ps.setString(4, row.instanceid());
            ps.setString(5, row.applicationDisplayName());
            ps.setString(6, row.nativeIdentity());
            ps.setString(7, row.accountDisplayName());
            setBool(ps, 8, row.active());
            setBool(ps, 9, row.locked());
            setBool(ps, 10, row.hasEntitlements());
            setBool(ps, 11, row.manuallyCorrelated());
            setTimestamp(ps, 12, row.lastRefresh());
            setTimestamp(ps, 13, row.createdAt());
            setTimestamp(ps, 14, row.modifiedAt());
            ps.setString(15, row.attributesJson());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private Set<String> readUuidColumn(Connection conn, String qualifiedTable, String column)
            throws SQLException {
        Set<String> ids = new HashSet<>();
        if (!tableExists(conn, qualifiedTable)) {
            return ids;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + "::text FROM " + qualifiedTable)) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) {
                    ids.add(v);
                }
            }
        }
        return ids;
    }

    private boolean tableExists(Connection conn, String qualifiedTable) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?)")) {
            ps.setString(1, qualifiedTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    private static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
