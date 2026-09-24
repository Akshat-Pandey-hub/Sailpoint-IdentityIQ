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
 * Plain-JDBC persistence for native policy-constraint rows into {@code <schema>.kf_policy_constraint}.
 * Each row is a constraint of a parent Policy (explicit {@code policy_id}). Idempotent: PK
 * {@code policyconstraintid} is the deterministic canonical UUID of the constraint id (with a stable
 * policy+name+type fallback); {@code INSERT … ON CONFLICT DO UPDATE}. Deterministic {@code record_hash}.
 */
public final class NativePolicyConstraintRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativePolicyConstraintRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_policy_constraint";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "policyconstraintid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "policy_id text, "
                        + "policy_name text, "
                        + "name text, "
                        + "description text, "
                        + "constraint_type text, "
                        + "weight integer, "
                        + "compensating_control text, "
                        + "violation_owner_id text, "
                        + "violation_owner_name text, "
                        + "violation_owner_type text, "
                        + "left_bundles jsonb, "
                        + "right_bundles jsonb, "
                        + "selectors jsonb, "
                        + "selector_count integer, "
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
                        + "policyconstraintid, source_id, policy_id, policy_name, name, description, constraint_type, "
                        + "weight, compensating_control, violation_owner_id, violation_owner_name, violation_owner_type, "
                        + "left_bundles, right_bundles, selectors, selector_count, arguments, created_at, modified_at, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (policyconstraintid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, policy_id = EXCLUDED.policy_id, "
                        + "policy_name = EXCLUDED.policy_name, name = EXCLUDED.name, description = EXCLUDED.description, "
                        + "constraint_type = EXCLUDED.constraint_type, weight = EXCLUDED.weight, "
                        + "compensating_control = EXCLUDED.compensating_control, "
                        + "violation_owner_id = EXCLUDED.violation_owner_id, "
                        + "violation_owner_name = EXCLUDED.violation_owner_name, "
                        + "violation_owner_type = EXCLUDED.violation_owner_type, left_bundles = EXCLUDED.left_bundles, "
                        + "right_bundles = EXCLUDED.right_bundles, selectors = EXCLUDED.selectors, "
                        + "selector_count = EXCLUDED.selector_count, arguments = EXCLUDED.arguments, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
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

    /** Deterministic PK: canonical UUID of the constraint id, else stable over policy|name|type. */
    public static String canonicalConstraintId(NativePolicyConstraintRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-policy-constraint|" + r.policyId + "|"
                    + (r.sourceId != null ? r.sourceId : r.name) + "|" + r.constraintType);
        }
        return id;
    }

    public static String recordHash(NativePolicyConstraintRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("policy_id", r.policyId);
        b.put("name", r.name);
        b.put("description", r.description);
        b.put("constraint_type", r.constraintType);
        b.put("weight", r.weight);
        b.put("compensating_control", r.compensatingControl);
        b.put("violation_owner_id", r.violationOwnerId);
        b.put("violation_owner_type", r.violationOwnerType);
        b.put("left_bundles", r.leftBundlesJson);
        b.put("right_bundles", r.rightBundlesJson);
        b.put("selectors", r.selectorsJson);
        b.put("selector_count", r.selectorCount);
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

    public UpsertOutcome upsert(Connection conn, NativePolicyConstraintRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalConstraintId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.policyId);
            ps.setString(i++, r.policyName);
            ps.setString(i++, r.name);
            ps.setString(i++, r.description);
            ps.setString(i++, r.constraintType);
            setInt(ps, i++, r.weight);
            ps.setString(i++, r.compensatingControl);
            ps.setString(i++, r.violationOwnerId);
            ps.setString(i++, r.violationOwnerName);
            ps.setString(i++, r.violationOwnerType);
            ps.setString(i++, r.leftBundlesJson);
            ps.setString(i++, r.rightBundlesJson);
            ps.setString(i++, r.selectorsJson);
            setInt(ps, i++, r.selectorCount);
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

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
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
