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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-JDBC persistence for native identity-role edges into {@code <schema>.kf_identity_role} (current-state:
 * idempotent upsert; soft-delete via the shared sweeper). PK is deterministic over
 * {@code identity_id | role_id | relationship_type | assignment_id} (edges are not standalone persisted
 * objects). Business-content {@code record_hash} for change detection.
 */
public final class NativeIdentityRoleRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityRoleRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_role";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "identityroleid uuid PRIMARY KEY, identity_id text, identity_name text, role_id text, "
                        + "role_name text, relationship_type text, assignment_id text, detection_assignment_ids text, "
                        + "comments text, future_assignment boolean, promoted_soft_permit boolean, "
                        + "detection_date timestamptz, targets jsonb, record_hash text, source_system text, "
                        + "source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "identityroleid, identity_id, identity_name, role_id, role_name, relationship_type, "
                        + "assignment_id, detection_assignment_ids, comments, future_assignment, promoted_soft_permit, "
                        + "detection_date, targets, record_hash, source_system, source_interface, source_object_type, "
                        + "extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (identityroleid) DO UPDATE SET "
                        + "identity_id = EXCLUDED.identity_id, identity_name = EXCLUDED.identity_name, "
                        + "role_id = EXCLUDED.role_id, role_name = EXCLUDED.role_name, "
                        + "relationship_type = EXCLUDED.relationship_type, assignment_id = EXCLUDED.assignment_id, "
                        + "detection_assignment_ids = EXCLUDED.detection_assignment_ids, comments = EXCLUDED.comments, "
                        + "future_assignment = EXCLUDED.future_assignment, "
                        + "promoted_soft_permit = EXCLUDED.promoted_soft_permit, "
                        + "detection_date = EXCLUDED.detection_date, targets = EXCLUDED.targets, "
                        + "record_hash = EXCLUDED.record_hash, source_system = EXCLUDED.source_system, "
                        + "source_interface = EXCLUDED.source_interface, source_object_type = EXCLUDED.source_object_type, "
                        + "extraction_run_id = EXCLUDED.extraction_run_id, extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    /** Deterministic PK over (identity_id | role_id | relationship_type | assignment_id). Never random. */
    public static String canonicalEdgeId(NativeIdentityRoleRecord r) {
        return ParquetIds.deterministicUuid("native-identity-role|" + r.identityId + "|" + r.roleId
                + "|" + r.relationshipType + "|" + r.assignmentId);
    }

    public static String recordHash(NativeIdentityRoleRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("identity_id", r.identityId);
        b.put("role_id", r.roleId);
        b.put("role_name", r.roleName);
        b.put("relationship_type", r.relationshipType);
        b.put("assignment_id", r.assignmentId);
        b.put("detection_assignment_ids", r.detectionAssignmentIds);
        b.put("comments", r.comments);
        b.put("future_assignment", r.futureAssignment);
        b.put("promoted_soft_permit", r.promotedSoftPermit);
        b.put("detection_date", r.detectionDate);
        b.put("targets", r.targetsJson);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeIdentityRoleRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalEdgeId(r));
            ps.setString(i++, r.identityId);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.roleId);
            ps.setString(i++, r.roleName);
            ps.setString(i++, r.relationshipType);
            ps.setString(i++, r.assignmentId);
            ps.setString(i++, r.detectionAssignmentIds);
            ps.setString(i++, r.comments);
            setBool(ps, i++, r.futureAssignment);
            setBool(ps, i++, r.promotedSoftPermit);
            setTs(ps, i++, r.detectionDate);
            ps.setString(i++, r.targetsJson);
            ps.setString(i++, recordHash(r));
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
