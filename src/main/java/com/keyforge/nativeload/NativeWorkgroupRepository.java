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
 * Plain-JDBC persistence for native Workgroup rows into {@code <schema>.kf_workgroup} (HLD table name;
 * REST PK column {@code workgroupid} mirrored so the schemas join). Native-shaped: scalar columns +
 * {@code jsonb} for capabilities/attributes.
 *
 * <p>Idempotent: PK {@code workgroupid} is the deterministic canonical UUID of the workgroup Identity
 * id (same derivation as the REST path); every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each
 * row carries a deterministic {@code record_hash} over its business fields for change detection.
 */
public final class NativeWorkgroupRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeWorkgroupRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workgroup";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "workgroupid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "display_name text, "
                        + "displayable_name text, "
                        + "email text, "
                        + "type text, "
                        + "description text, "
                        + "notification_option text, "
                        + "inactive boolean, "
                        + "is_workgroup boolean, "
                        + "owner_id text, "
                        + "owner_name text, "
                        + "capabilities jsonb, "
                        + "attributes jsonb, "
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
                        + "workgroupid, source_id, name, display_name, displayable_name, email, type, description, "
                        + "notification_option, inactive, is_workgroup, owner_id, owner_name, capabilities, "
                        + "attributes, created_at, modified_at, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, "
                        + "?, ?) "
                        + "ON CONFLICT (workgroupid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, "
                        + "display_name = EXCLUDED.display_name, displayable_name = EXCLUDED.displayable_name, "
                        + "email = EXCLUDED.email, type = EXCLUDED.type, description = EXCLUDED.description, "
                        + "notification_option = EXCLUDED.notification_option, inactive = EXCLUDED.inactive, "
                        + "is_workgroup = EXCLUDED.is_workgroup, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, capabilities = EXCLUDED.capabilities, "
                        + "attributes = EXCLUDED.attributes, created_at = EXCLUDED.created_at, "
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

    /** Deterministic PK: canonical UUID of the source id, with a stable name-based fallback. Never random. */
    public static String canonicalWorkgroupId(NativeWorkgroupRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-workgroup|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeWorkgroupRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("display_name", r.displayName);
        b.put("displayable_name", r.displayableName);
        b.put("email", r.email);
        b.put("type", r.type);
        b.put("description", r.description);
        b.put("notification_option", r.notificationOption);
        b.put("inactive", r.inactive);
        b.put("is_workgroup", r.workgroup);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("capabilities", r.capabilitiesJson);
        b.put("attributes", r.attributesJson);
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

    public UpsertOutcome upsert(Connection conn, NativeWorkgroupRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalWorkgroupId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.displayableName);
            ps.setString(i++, r.email);
            ps.setString(i++, r.type);
            ps.setString(i++, r.description);
            ps.setString(i++, r.notificationOption);
            setBool(ps, i++, r.inactive);
            setBool(ps, i++, r.workgroup);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.capabilitiesJson);
            ps.setString(i++, r.attributesJson);
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
