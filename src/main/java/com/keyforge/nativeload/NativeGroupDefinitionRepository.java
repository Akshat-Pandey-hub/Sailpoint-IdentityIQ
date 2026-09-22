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
 * Plain-JDBC persistence for native GroupDefinition rows into {@code <schema>.kf_group_definition}.
 * A single table holds both Populations and Groups, distinguished by the source-backed {@code type}
 * column ({@code GROUP}/{@code POPULATION}) plus the raw {@code factory_*} evidence — no duplicate rows.
 *
 * <p>Idempotent: PK {@code groupid} is the deterministic canonical UUID of the GroupDefinition id;
 * every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row carries a deterministic
 * {@code record_hash} over its business fields for change detection.
 */
public final class NativeGroupDefinitionRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeGroupDefinitionRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_group_definition";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "groupid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "type text, "
                        + "factory_id text, "
                        + "factory_name text, "
                        + "filter_expression text, "
                        + "is_private boolean, "
                        + "indexed boolean, "
                        + "null_group boolean, "
                        + "name_unique boolean, "
                        + "owner_id text, "
                        + "owner_name text, "
                        + "last_refresh timestamptz, "
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
                        + "groupid, source_id, name, type, factory_id, factory_name, filter_expression, "
                        + "is_private, indexed, null_group, name_unique, owner_id, owner_name, last_refresh, "
                        + "created_at, modified_at, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (groupid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, type = EXCLUDED.type, "
                        + "factory_id = EXCLUDED.factory_id, factory_name = EXCLUDED.factory_name, "
                        + "filter_expression = EXCLUDED.filter_expression, is_private = EXCLUDED.is_private, "
                        + "indexed = EXCLUDED.indexed, null_group = EXCLUDED.null_group, "
                        + "name_unique = EXCLUDED.name_unique, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, last_refresh = EXCLUDED.last_refresh, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "record_hash = EXCLUDED.record_hash, source_system = EXCLUDED.source_system, "
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

    /** Deterministic PK: canonical UUID of the source id, with a stable name-based fallback. Never random. */
    public static String canonicalGroupId(NativeGroupDefinitionRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-groupdefinition|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeGroupDefinitionRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("factory_id", r.factoryId);
        b.put("factory_name", r.factoryName);
        b.put("filter_expression", r.filterExpression);
        b.put("is_private", r.isPrivate);
        b.put("indexed", r.indexed);
        b.put("null_group", r.nullGroup);
        b.put("name_unique", r.nameUnique);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("last_refresh", r.lastRefresh);
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

    public UpsertOutcome upsert(Connection conn, NativeGroupDefinitionRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalGroupId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.factoryId);
            ps.setString(i++, r.factoryName);
            ps.setString(i++, r.filterExpression);
            setBool(ps, i++, r.isPrivate);
            setBool(ps, i++, r.indexed);
            setBool(ps, i++, r.nullGroup);
            setBool(ps, i++, r.nameUnique);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            setTs(ps, i++, r.lastRefresh);
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
