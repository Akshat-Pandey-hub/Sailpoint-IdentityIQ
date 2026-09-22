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
 * Plain-JDBC persistence for native Link (account) rows into {@code <schema>.kf_account} (HLD table
 * name; REST PK column {@code accountid} mirrored so the schemas join). Native-shaped: rich scalar
 * columns + {@code jsonb} for permissions/target_permissions/attributes/entitlement_attributes.
 *
 * <p>Idempotent: PK {@code accountid} is the deterministic canonical UUID of the Link id (same
 * derivation as the REST path); every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row
 * carries a deterministic {@code record_hash} over its business fields for change detection.
 */
public final class NativeLinkRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeLinkRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_account";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "accountid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "link_uuid text, "
                        + "native_identity text, "
                        + "display_name text, "
                        + "displayable_name text, "
                        + "instance text, "
                        + "component_ids text, "
                        + "application_id text, "
                        + "application_name text, "
                        + "identity_id text, "
                        + "identity_name text, "
                        + "disabled boolean, "
                        + "locked boolean, "
                        + "composite boolean, "
                        + "manually_correlated boolean, "
                        + "has_entitlements boolean, "
                        + "iiq_disabled boolean, "
                        + "iiq_locked boolean, "
                        + "permissions jsonb, "
                        + "target_permissions jsonb, "
                        + "attributes jsonb, "
                        + "entitlement_attributes jsonb, "
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
                        + "accountid, source_id, link_uuid, native_identity, display_name, displayable_name, "
                        + "instance, component_ids, application_id, application_name, identity_id, identity_name, "
                        + "disabled, locked, composite, manually_correlated, has_entitlements, iiq_disabled, "
                        + "iiq_locked, permissions, target_permissions, attributes, entitlement_attributes, "
                        + "created_at, modified_at, last_refresh, last_target_aggregation, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (accountid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, link_uuid = EXCLUDED.link_uuid, "
                        + "native_identity = EXCLUDED.native_identity, display_name = EXCLUDED.display_name, "
                        + "displayable_name = EXCLUDED.displayable_name, instance = EXCLUDED.instance, "
                        + "component_ids = EXCLUDED.component_ids, application_id = EXCLUDED.application_id, "
                        + "application_name = EXCLUDED.application_name, identity_id = EXCLUDED.identity_id, "
                        + "identity_name = EXCLUDED.identity_name, disabled = EXCLUDED.disabled, "
                        + "locked = EXCLUDED.locked, composite = EXCLUDED.composite, "
                        + "manually_correlated = EXCLUDED.manually_correlated, "
                        + "has_entitlements = EXCLUDED.has_entitlements, iiq_disabled = EXCLUDED.iiq_disabled, "
                        + "iiq_locked = EXCLUDED.iiq_locked, permissions = EXCLUDED.permissions, "
                        + "target_permissions = EXCLUDED.target_permissions, attributes = EXCLUDED.attributes, "
                        + "entitlement_attributes = EXCLUDED.entitlement_attributes, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "last_refresh = EXCLUDED.last_refresh, "
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
    public static String canonicalAccountId(NativeLinkRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-link|"
                    + (r.sourceId == null ? (r.applicationName + "|" + r.nativeIdentity) : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeLinkRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("link_uuid", r.uuid);
        b.put("native_identity", r.nativeIdentity);
        b.put("display_name", r.displayName);
        b.put("displayable_name", r.displayableName);
        b.put("instance", r.instance);
        b.put("component_ids", r.componentIds);
        b.put("application_id", r.applicationId);
        b.put("application_name", r.applicationName);
        b.put("identity_id", r.identityId);
        b.put("identity_name", r.identityName);
        b.put("disabled", r.disabled);
        b.put("locked", r.locked);
        b.put("composite", r.composite);
        b.put("manually_correlated", r.manuallyCorrelated);
        b.put("has_entitlements", r.hasEntitlements);
        b.put("iiq_disabled", r.iiqDisabled);
        b.put("iiq_locked", r.iiqLocked);
        b.put("permissions", r.permissionsJson);
        b.put("target_permissions", r.targetPermissionsJson);
        b.put("attributes", r.attributesJson);
        b.put("entitlement_attributes", r.entitlementAttributesJson);
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

    public UpsertOutcome upsert(Connection conn, NativeLinkRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalAccountId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.uuid);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.displayableName);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.componentIds);
            ps.setString(i++, r.applicationId);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.identityId);
            ps.setString(i++, r.identityName);
            setBool(ps, i++, r.disabled);
            setBool(ps, i++, r.locked);
            setBool(ps, i++, r.composite);
            setBool(ps, i++, r.manuallyCorrelated);
            setBool(ps, i++, r.hasEntitlements);
            setBool(ps, i++, r.iiqDisabled);
            setBool(ps, i++, r.iiqLocked);
            ps.setString(i++, r.permissionsJson);
            ps.setString(i++, r.targetPermissionsJson);
            ps.setString(i++, r.attributesJson);
            ps.setString(i++, r.entitlementAttributesJson);
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
