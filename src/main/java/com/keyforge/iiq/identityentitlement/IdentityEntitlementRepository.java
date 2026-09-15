package com.keyforge.iiq.identityentitlement;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code kf_identity_entitlement} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic {@code id}.
 * The authoritative-provenance columns exist (per the PDF) but are written NULL until an
 * authoritative source provides them.
 */
public class IdentityEntitlementRepository {

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

    public IdentityEntitlementRepository() {
        this(DEFAULT_SCHEMA);
    }

    public IdentityEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "identity_id uuid, "
                        + "identity_display_name text, "
                        + "application_id uuid, "
                        + "application_name text, "
                        + "entitlement_id uuid, "
                        + "entitlement_value text, "
                        + "entitlement_type text, "
                        + "source_attribute text, "
                        + "resolution_status text, "
                        + "source text, "
                        + "assigner text, "
                        + "assigned_date timestamptz, "
                        + "end_date timestamptz, "
                        + "aggregation_state text, "
                        + "granted_by_role text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (id, identity_id, identity_display_name, application_id, application_name, "
                        + "entitlement_id, entitlement_value, entitlement_type, source_attribute, resolution_status, "
                        + "source, assigner, assigned_date, end_date, aggregation_state, granted_by_role) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "identity_id = EXCLUDED.identity_id, identity_display_name = EXCLUDED.identity_display_name, "
                        + "application_id = EXCLUDED.application_id, application_name = EXCLUDED.application_name, "
                        + "entitlement_id = EXCLUDED.entitlement_id, entitlement_value = EXCLUDED.entitlement_value, "
                        + "entitlement_type = EXCLUDED.entitlement_type, source_attribute = EXCLUDED.source_attribute, "
                        + "resolution_status = EXCLUDED.resolution_status, source = EXCLUDED.source, "
                        + "assigner = EXCLUDED.assigner, assigned_date = EXCLUDED.assigned_date, "
                        + "end_date = EXCLUDED.end_date, aggregation_state = EXCLUDED.aggregation_state, "
                        + "granted_by_role = EXCLUDED.granted_by_role, extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, IdentityEntitlementRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.id());
            ps.setString(2, row.identityId());
            ps.setString(3, row.identityDisplayName());
            ps.setString(4, row.applicationId());
            ps.setString(5, row.applicationName());
            ps.setString(6, row.entitlementId());
            ps.setString(7, row.entitlementValue());
            ps.setString(8, row.entitlementType());
            ps.setString(9, row.sourceAttribute());
            ps.setString(10, row.resolutionStatus());
            ps.setString(11, row.source());
            ps.setString(12, row.assigner());
            setTimestamp(ps, 13, row.assignedDate());
            setTimestamp(ps, 14, row.endDate());
            ps.setString(15, row.aggregationState());
            ps.setString(16, row.grantedByRole());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
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
