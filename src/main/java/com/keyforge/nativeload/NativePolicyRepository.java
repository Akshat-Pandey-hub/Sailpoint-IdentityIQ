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
 * Plain-JDBC persistence for native Policy rows into {@code <schema>.kf_policy}. Idempotent: PK
 * {@code policyid} is the deterministic canonical UUID of the source id; {@code INSERT … ON CONFLICT DO
 * UPDATE}. Each row carries a deterministic {@code record_hash}.
 */
public final class NativePolicyRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativePolicyRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_policy";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "policyid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "type text, "
                        + "type_key text, "
                        + "description text, "
                        + "descriptions jsonb, "
                        + "executor text, "
                        + "violation_owner_id text, "
                        + "violation_owner_name text, "
                        + "constraint_count integer, "
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
                        + "policyid, source_id, name, type, type_key, description, descriptions, executor, "
                        + "violation_owner_id, violation_owner_name, constraint_count, created_at, modified_at, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (policyid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, type = EXCLUDED.type, "
                        + "type_key = EXCLUDED.type_key, description = EXCLUDED.description, "
                        + "descriptions = EXCLUDED.descriptions, executor = EXCLUDED.executor, "
                        + "violation_owner_id = EXCLUDED.violation_owner_id, "
                        + "violation_owner_name = EXCLUDED.violation_owner_name, "
                        + "constraint_count = EXCLUDED.constraint_count, created_at = EXCLUDED.created_at, "
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

    public static String canonicalPolicyId(NativePolicyRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-policy|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativePolicyRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("type_key", r.typeKey);
        b.put("description", r.description);
        b.put("descriptions", r.descriptionsJson);
        b.put("executor", r.executor);
        b.put("violation_owner_id", r.violationOwnerId);
        b.put("violation_owner_name", r.violationOwnerName);
        b.put("constraint_count", r.constraintCount);
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

    public UpsertOutcome upsert(Connection conn, NativePolicyRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalPolicyId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.typeKey);
            ps.setString(i++, r.description);
            ps.setString(i++, r.descriptionsJson);
            ps.setString(i++, r.executor);
            ps.setString(i++, r.violationOwnerId);
            ps.setString(i++, r.violationOwnerName);
            setInt(ps, i++, r.constraintCount);
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
