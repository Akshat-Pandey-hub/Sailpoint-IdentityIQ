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
 * Plain-JDBC persistence for native ManagedAttribute rows into {@code <schema>.kf_entitlement} (HLD
 * table name; REST PK column {@code entitlementid} mirrored so the schemas join). Native-shaped:
 * richer columns + {@code jsonb} for descriptions/permissions/inheritance/associations/attributes.
 *
 * <p>Idempotent: PK {@code entitlementid} is the deterministic canonical UUID of the ManagedAttribute
 * id (same derivation as the REST path); every write is {@code INSERT … ON CONFLICT DO UPDATE}, so a
 * rerun updates in place and never duplicates. Each row carries a {@code record_hash} (deterministic
 * SHA-256 over its business fields) for change detection.
 */
public final class NativeManagedAttributeRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeManagedAttributeRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "entitlementid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "value text, "
                        + "display_name text, "
                        + "displayable_name text, "
                        + "attribute text, "
                        + "type text, "
                        + "ma_uuid text, "
                        + "reference_attribute text, "
                        + "purview text, "
                        + "application_id text, "
                        + "application_name text, "
                        + "instance text, "
                        + "native_identity text, "
                        + "requestable boolean, "
                        + "is_group boolean, "
                        + "is_permission boolean, "
                        + "uncorrelated boolean, "
                        + "aggregated boolean, "
                        + "iiq_elevated_access boolean, "
                        + "owner_id text, "
                        + "owner_name text, "
                        + "description text, "
                        + "descriptions jsonb, "
                        + "permissions jsonb, "
                        + "target_permissions jsonb, "
                        + "inheritance jsonb, "
                        + "associations jsonb, "
                        + "attributes jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "last_refresh timestamptz, "
                        + "last_target_aggregation timestamptz, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "entitlementid, source_id, name, value, display_name, displayable_name, attribute, type, "
                        + "ma_uuid, reference_attribute, purview, application_id, application_name, instance, "
                        + "native_identity, requestable, is_group, is_permission, uncorrelated, aggregated, "
                        + "iiq_elevated_access, owner_id, owner_name, description, descriptions, permissions, "
                        + "target_permissions, inheritance, associations, attributes, created_at, modified_at, "
                        + "last_refresh, last_target_aggregation, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (entitlementid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, value = EXCLUDED.value, "
                        + "display_name = EXCLUDED.display_name, displayable_name = EXCLUDED.displayable_name, "
                        + "attribute = EXCLUDED.attribute, type = EXCLUDED.type, ma_uuid = EXCLUDED.ma_uuid, "
                        + "reference_attribute = EXCLUDED.reference_attribute, purview = EXCLUDED.purview, "
                        + "application_id = EXCLUDED.application_id, application_name = EXCLUDED.application_name, "
                        + "instance = EXCLUDED.instance, native_identity = EXCLUDED.native_identity, "
                        + "requestable = EXCLUDED.requestable, is_group = EXCLUDED.is_group, "
                        + "is_permission = EXCLUDED.is_permission, uncorrelated = EXCLUDED.uncorrelated, "
                        + "aggregated = EXCLUDED.aggregated, iiq_elevated_access = EXCLUDED.iiq_elevated_access, "
                        + "owner_id = EXCLUDED.owner_id, owner_name = EXCLUDED.owner_name, "
                        + "description = EXCLUDED.description, descriptions = EXCLUDED.descriptions, "
                        + "permissions = EXCLUDED.permissions, target_permissions = EXCLUDED.target_permissions, "
                        + "inheritance = EXCLUDED.inheritance, associations = EXCLUDED.associations, "
                        + "attributes = EXCLUDED.attributes, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, last_refresh = EXCLUDED.last_refresh, "
                        + "last_target_aggregation = EXCLUDED.last_target_aggregation, "
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
    public static String canonicalEntitlementId(NativeManagedAttributeRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-managedattribute|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeManagedAttributeRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("value", r.value);
        b.put("display_name", r.displayName);
        b.put("displayable_name", r.displayableName);
        b.put("attribute", r.attribute);
        b.put("type", r.type);
        b.put("ma_uuid", r.uuid);
        b.put("reference_attribute", r.referenceAttribute);
        b.put("purview", r.purview);
        b.put("application_id", r.applicationId);
        b.put("application_name", r.applicationName);
        b.put("instance", r.instance);
        b.put("native_identity", r.nativeIdentity);
        b.put("requestable", r.requestable);
        b.put("is_group", r.group);
        b.put("is_permission", r.permission);
        b.put("uncorrelated", r.uncorrelated);
        b.put("aggregated", r.aggregated);
        b.put("iiq_elevated_access", r.iiqElevatedAccess);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("description", r.description);
        b.put("descriptions", r.descriptionsJson);
        b.put("permissions", r.permissionsJson);
        b.put("target_permissions", r.targetPermissionsJson);
        b.put("inheritance", r.inheritanceJson);
        b.put("associations", r.associationsJson);
        b.put("attributes", r.attributesJson);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        b.put("last_refresh", r.lastRefresh);
        b.put("last_target_aggregation", r.lastTargetAggregation);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeManagedAttributeRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalEntitlementId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.value);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.displayableName);
            ps.setString(i++, r.attribute);
            ps.setString(i++, r.type);
            ps.setString(i++, r.uuid);
            ps.setString(i++, r.referenceAttribute);
            ps.setString(i++, r.purview);
            ps.setString(i++, r.applicationId);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.nativeIdentity);
            setBool(ps, i++, r.requestable);
            setBool(ps, i++, r.group);
            setBool(ps, i++, r.permission);
            setBool(ps, i++, r.uncorrelated);
            setBool(ps, i++, r.aggregated);
            setBool(ps, i++, r.iiqElevatedAccess);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.description);
            ps.setString(i++, r.descriptionsJson);
            ps.setString(i++, r.permissionsJson);
            ps.setString(i++, r.targetPermissionsJson);
            ps.setString(i++, r.inheritanceJson);
            ps.setString(i++, r.associationsJson);
            ps.setString(i++, r.attributesJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            setTs(ps, i++, r.lastRefresh);
            setTs(ps, i++, r.lastTargetAggregation);
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
