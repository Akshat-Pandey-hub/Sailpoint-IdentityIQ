package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.parquet.ParquetIds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Plain-JDBC persistence for native Identity rows into {@code <schema>.kf_identity} (HLD table name),
 * in the separate {@code iiq_native} schema. This project owns and creates the table by explicit DDL;
 * it is native-shaped (richer columns + {@code jsonb} for the role graph, capabilities, scopes and the
 * full attribute map) rather than the REST/SCIM shape.
 *
 * <p>Idempotent: the primary key {@code userid} is the deterministic canonical UUID of the IIQ
 * Identity id — the same derivation the REST path uses ({@link ParquetIds#canonicalUuid}) so the two
 * schemas join on the same key — and every write is an {@code INSERT … ON CONFLICT (userid) DO UPDATE}.
 * Re-running extraction updates rows in place; it never duplicates them.
 */
public final class NativeIdentityRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "userid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "display_name text, "
                        + "displayable_name text, "
                        + "first_name text, "
                        + "last_name text, "
                        + "email text, "
                        + "inactive boolean, "
                        + "type text, "
                        + "correlated boolean, "
                        + "manager_status boolean, "
                        + "manager_id text, "
                        + "manager_name text, "
                        + "administrator_id text, "
                        + "administrator_name text, "
                        + "accounts jsonb, "
                        + "assigned_roles jsonb, "
                        + "detected_roles jsonb, "
                        + "role_assignments jsonb, "
                        + "role_detections jsonb, "
                        + "capabilities jsonb, "
                        + "controlled_scopes jsonb, "
                        + "attributes jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "last_refresh timestamptz, "
                        + "last_login timestamptz, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "userid, source_id, name, display_name, displayable_name, first_name, last_name, email, "
                        + "inactive, type, correlated, manager_status, manager_id, manager_name, "
                        + "administrator_id, administrator_name, accounts, assigned_roles, detected_roles, "
                        + "role_assignments, role_detections, capabilities, controlled_scopes, attributes, "
                        + "created_at, modified_at, last_refresh, last_login, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (userid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, "
                        + "display_name = EXCLUDED.display_name, displayable_name = EXCLUDED.displayable_name, "
                        + "first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, "
                        + "email = EXCLUDED.email, inactive = EXCLUDED.inactive, type = EXCLUDED.type, "
                        + "correlated = EXCLUDED.correlated, manager_status = EXCLUDED.manager_status, "
                        + "manager_id = EXCLUDED.manager_id, manager_name = EXCLUDED.manager_name, "
                        + "administrator_id = EXCLUDED.administrator_id, "
                        + "administrator_name = EXCLUDED.administrator_name, accounts = EXCLUDED.accounts, "
                        + "assigned_roles = EXCLUDED.assigned_roles, detected_roles = EXCLUDED.detected_roles, "
                        + "role_assignments = EXCLUDED.role_assignments, role_detections = EXCLUDED.role_detections, "
                        + "capabilities = EXCLUDED.capabilities, controlled_scopes = EXCLUDED.controlled_scopes, "
                        + "attributes = EXCLUDED.attributes, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, last_refresh = EXCLUDED.last_refresh, "
                        + "last_login = EXCLUDED.last_login, source_system = EXCLUDED.source_system, "
                        + "source_interface = EXCLUDED.source_interface, "
                        + "source_object_type = EXCLUDED.source_object_type, "
                        + "extraction_run_id = EXCLUDED.extraction_run_id, extracted_at = now() "
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

    /**
     * The deterministic primary key for a native Identity record: the canonical UUID of the IIQ id
     * (same derivation the REST path uses, so the two schemas join on the same key); an unparseable or
     * missing id falls back to a stable name-based UUID. Never random — re-running never duplicates.
     */
    public static String canonicalUserid(NativeIdentityRecord r) {
        String userid = ParquetIds.canonicalUuid(r.sourceId);
        if (userid == null) {
            userid = ParquetIds.deterministicUuid("native-identity|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return userid;
    }

    /**
     * Idempotent upsert. The {@code userid} is {@link #canonicalUserid(NativeIdentityRecord)}, so a
     * rerun updates the same row rather than inserting a duplicate.
     */
    public UpsertOutcome upsert(Connection conn, NativeIdentityRecord r) throws SQLException {
        String userid = canonicalUserid(r);
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, userid);
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.displayableName);
            ps.setString(i++, r.firstName);
            ps.setString(i++, r.lastName);
            ps.setString(i++, r.email);
            setBool(ps, i++, r.inactive);
            ps.setString(i++, r.type);
            setBool(ps, i++, r.correlated);
            setBool(ps, i++, r.managerStatus);
            ps.setString(i++, r.managerId);
            ps.setString(i++, r.managerName);
            ps.setString(i++, r.administratorId);
            ps.setString(i++, r.administratorName);
            ps.setString(i++, r.accountsJson);
            ps.setString(i++, r.assignedRolesJson);
            ps.setString(i++, r.detectedRolesJson);
            ps.setString(i++, r.roleAssignmentsJson);
            ps.setString(i++, r.roleDetectionsJson);
            ps.setString(i++, r.capabilitiesJson);
            ps.setString(i++, r.controlledScopesJson);
            ps.setString(i++, r.attributesJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            setTs(ps, i++, r.lastRefresh);
            setTs(ps, i++, r.lastLogin);
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.srcObjectType);
            ps.setString(i, r.extractionRunId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
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

    private static void setTs(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, value.atOffset(ZoneOffset.UTC));
        }
    }
}
