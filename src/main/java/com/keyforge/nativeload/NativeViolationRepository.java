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
 * Plain-JDBC persistence for native PolicyViolation rows into {@code <schema>.kf_violation}. Idempotent:
 * PK {@code violationid} is the deterministic canonical UUID of the source id; every write is
 * {@code INSERT … ON CONFLICT DO UPDATE}. Each row carries a deterministic {@code record_hash}.
 */
public final class NativeViolationRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeViolationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_violation";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "violationid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "identity_id text, "
                        + "identity_name text, "
                        + "policy_id text, "
                        + "policy_name text, "
                        + "constraint_id text, "
                        + "constraint_name text, "
                        + "status text, "
                        + "active boolean, "
                        + "left_bundles text, "
                        + "right_bundles text, "
                        + "entitlements_marked_for_remediation text, "
                        + "bundles_marked_for_remediation text, "
                        + "relevant_apps jsonb, "
                        + "violating_entitlements jsonb, "
                        + "arguments jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "violationid, source_id, name, identity_id, identity_name, policy_id, policy_name, "
                        + "constraint_id, constraint_name, status, active, left_bundles, right_bundles, "
                        + "entitlements_marked_for_remediation, bundles_marked_for_remediation, relevant_apps, "
                        + "violating_entitlements, arguments, created_at, modified_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (violationid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, identity_id = EXCLUDED.identity_id, "
                        + "identity_name = EXCLUDED.identity_name, policy_id = EXCLUDED.policy_id, "
                        + "policy_name = EXCLUDED.policy_name, constraint_id = EXCLUDED.constraint_id, "
                        + "constraint_name = EXCLUDED.constraint_name, status = EXCLUDED.status, "
                        + "active = EXCLUDED.active, left_bundles = EXCLUDED.left_bundles, "
                        + "right_bundles = EXCLUDED.right_bundles, "
                        + "entitlements_marked_for_remediation = EXCLUDED.entitlements_marked_for_remediation, "
                        + "bundles_marked_for_remediation = EXCLUDED.bundles_marked_for_remediation, "
                        + "relevant_apps = EXCLUDED.relevant_apps, violating_entitlements = EXCLUDED.violating_entitlements, "
                        + "arguments = EXCLUDED.arguments, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, record_hash = EXCLUDED.record_hash, "
                        + "source_system = EXCLUDED.source_system, source_interface = EXCLUDED.source_interface, "
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

    public static String canonicalViolationId(NativeViolationRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-violation|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativeViolationRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("identity_id", r.identityId);
        b.put("identity_name", r.identityName);
        b.put("policy_id", r.policyId);
        b.put("policy_name", r.policyName);
        b.put("constraint_id", r.constraintId);
        b.put("constraint_name", r.constraintName);
        b.put("status", r.status);
        b.put("active", r.active);
        b.put("left_bundles", r.leftBundles);
        b.put("right_bundles", r.rightBundles);
        b.put("entitlements_marked_for_remediation", r.entitlementsMarkedForRemediation);
        b.put("bundles_marked_for_remediation", r.bundlesMarkedForRemediation);
        b.put("relevant_apps", r.relevantAppsJson);
        b.put("violating_entitlements", r.violatingEntitlementsJson);
        b.put("arguments", r.argumentsJson);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeViolationRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalViolationId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.identityId);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.policyId);
            ps.setString(i++, r.policyName);
            ps.setString(i++, r.constraintId);
            ps.setString(i++, r.constraintName);
            ps.setString(i++, r.status);
            setBool(ps, i++, r.active);
            ps.setString(i++, r.leftBundles);
            ps.setString(i++, r.rightBundles);
            ps.setString(i++, r.entitlementsMarkedForRemediation);
            ps.setString(i++, r.bundlesMarkedForRemediation);
            ps.setString(i++, r.relevantAppsJson);
            ps.setString(i++, r.violatingEntitlementsJson);
            ps.setString(i++, r.argumentsJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
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
