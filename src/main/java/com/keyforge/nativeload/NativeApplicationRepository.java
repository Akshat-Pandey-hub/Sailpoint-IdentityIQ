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
 * Plain-JDBC persistence for native Application rows into {@code <schema>.kf_application} (HLD table
 * name; REST PK column {@code applicationid} mirrored so the schemas join). Native-shaped: rich scalar
 * columns + {@code jsonb} for owners/remediators/dependencies/schemas/descriptions/attributes.
 *
 * <p>Idempotent: PK {@code applicationid} is the deterministic canonical UUID of the Application id
 * (same derivation as the REST path); every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row
 * carries a deterministic {@code record_hash} over its business fields for change detection.
 */
public final class NativeApplicationRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeApplicationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_application";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "applicationid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "description text, "
                        + "type text, "
                        + "connector text, "
                        + "features_string text, "
                        + "profile_class text, "
                        + "proxied_name text, "
                        + "cluster text, "
                        + "icon text, "
                        + "aggregation_types text, "
                        + "before_provisioning_rule text, "
                        + "after_provisioning_rule text, "
                        + "account_schema_correlation_rule text, "
                        + "account_schema_customization_rule text, "
                        + "account_schema_creation_rule text, "
                        + "account_schema_refresh_rule text, "
                        + "application_creation_rule text, "
                        + "account_schema_correlation_rule_id text, "
                        + "account_schema_customization_rule_id text, "
                        + "account_schema_creation_rule_id text, "
                        + "account_schema_refresh_rule_id text, "
                        + "application_creation_rule_id text, "
                        + "score integer, "
                        + "authoritative boolean, "
                        + "case_insensitive boolean, "
                        + "logical boolean, "
                        + "composite boolean, "
                        + "authentication_resource boolean, "
                        + "activity_enabled boolean, "
                        + "in_maintenance boolean, "
                        + "manages_other_apps boolean, "
                        + "native_change_detection_enabled boolean, "
                        + "supports_provisioning boolean, "
                        + "supports_account_only boolean, "
                        + "supports_additional_accounts boolean, "
                        + "supports_authenticate boolean, "
                        + "supports_group_provisioning boolean, "
                        + "supports_direct_permissions boolean, "
                        + "sync_provisioning boolean, "
                        + "owner_id text, "
                        + "owner_name text, "
                        + "secondary_owners jsonb, "
                        + "remediators jsonb, "
                        + "dependencies jsonb, "
                        + "schemas jsonb, "
                        + "descriptions jsonb, "
                        + "attributes jsonb, "
                        + "provisioning_config text, "
                        + "account_correlation_config text, "
                        + "manager_correlation_rule text, "
                        + "manager_correlation_filter text, "
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
                        + "applicationid, source_id, name, description, type, connector, features_string, "
                        + "profile_class, proxied_name, cluster, icon, aggregation_types, "
                        + "before_provisioning_rule, after_provisioning_rule, "
                        + "account_schema_correlation_rule, account_schema_customization_rule, "
                        + "account_schema_creation_rule, account_schema_refresh_rule, application_creation_rule, "
                        + "account_schema_correlation_rule_id, account_schema_customization_rule_id, "
                        + "account_schema_creation_rule_id, account_schema_refresh_rule_id, application_creation_rule_id, "
                        + "score, authoritative, "
                        + "case_insensitive, logical, composite, authentication_resource, activity_enabled, "
                        + "in_maintenance, manages_other_apps, native_change_detection_enabled, "
                        + "supports_provisioning, supports_account_only, supports_additional_accounts, "
                        + "supports_authenticate, supports_group_provisioning, supports_direct_permissions, "
                        + "sync_provisioning, owner_id, owner_name, secondary_owners, remediators, "
                        + "dependencies, schemas, descriptions, attributes, created_at, modified_at, "
                        + "provisioning_config, account_correlation_config, manager_correlation_rule, "
                        + "manager_correlation_filter, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, "
                        + "?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (applicationid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, description = EXCLUDED.description, "
                        + "type = EXCLUDED.type, connector = EXCLUDED.connector, "
                        + "features_string = EXCLUDED.features_string, profile_class = EXCLUDED.profile_class, "
                        + "proxied_name = EXCLUDED.proxied_name, cluster = EXCLUDED.cluster, icon = EXCLUDED.icon, "
                        + "aggregation_types = EXCLUDED.aggregation_types, "
                        + "before_provisioning_rule = EXCLUDED.before_provisioning_rule, "
                        + "after_provisioning_rule = EXCLUDED.after_provisioning_rule, "
                        + "account_schema_correlation_rule = EXCLUDED.account_schema_correlation_rule, "
                        + "account_schema_customization_rule = EXCLUDED.account_schema_customization_rule, "
                        + "account_schema_creation_rule = EXCLUDED.account_schema_creation_rule, "
                        + "account_schema_refresh_rule = EXCLUDED.account_schema_refresh_rule, "
                        + "application_creation_rule = EXCLUDED.application_creation_rule, "
                        + "account_schema_correlation_rule_id = EXCLUDED.account_schema_correlation_rule_id, "
                        + "account_schema_customization_rule_id = EXCLUDED.account_schema_customization_rule_id, "
                        + "account_schema_creation_rule_id = EXCLUDED.account_schema_creation_rule_id, "
                        + "account_schema_refresh_rule_id = EXCLUDED.account_schema_refresh_rule_id, "
                        + "application_creation_rule_id = EXCLUDED.application_creation_rule_id, score = EXCLUDED.score, "
                        + "authoritative = EXCLUDED.authoritative, case_insensitive = EXCLUDED.case_insensitive, "
                        + "logical = EXCLUDED.logical, composite = EXCLUDED.composite, "
                        + "authentication_resource = EXCLUDED.authentication_resource, "
                        + "activity_enabled = EXCLUDED.activity_enabled, in_maintenance = EXCLUDED.in_maintenance, "
                        + "manages_other_apps = EXCLUDED.manages_other_apps, "
                        + "native_change_detection_enabled = EXCLUDED.native_change_detection_enabled, "
                        + "supports_provisioning = EXCLUDED.supports_provisioning, "
                        + "supports_account_only = EXCLUDED.supports_account_only, "
                        + "supports_additional_accounts = EXCLUDED.supports_additional_accounts, "
                        + "supports_authenticate = EXCLUDED.supports_authenticate, "
                        + "supports_group_provisioning = EXCLUDED.supports_group_provisioning, "
                        + "supports_direct_permissions = EXCLUDED.supports_direct_permissions, "
                        + "sync_provisioning = EXCLUDED.sync_provisioning, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, secondary_owners = EXCLUDED.secondary_owners, "
                        + "remediators = EXCLUDED.remediators, dependencies = EXCLUDED.dependencies, "
                        + "schemas = EXCLUDED.schemas, descriptions = EXCLUDED.descriptions, "
                        + "attributes = EXCLUDED.attributes, "
                        + "provisioning_config = EXCLUDED.provisioning_config, "
                        + "account_correlation_config = EXCLUDED.account_correlation_config, "
                        + "manager_correlation_rule = EXCLUDED.manager_correlation_rule, "
                        + "manager_correlation_filter = EXCLUDED.manager_correlation_filter, "
                        + "created_at = EXCLUDED.created_at, "
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
    public static String canonicalApplicationId(NativeApplicationRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-application|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeApplicationRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("description", r.description);
        b.put("type", r.type);
        b.put("connector", r.connector);
        b.put("features_string", r.featuresString);
        b.put("profile_class", r.profileClass);
        b.put("proxied_name", r.proxiedName);
        b.put("cluster", r.cluster);
        b.put("icon", r.icon);
        b.put("aggregation_types", r.aggregationTypes);
        b.put("before_provisioning_rule", r.beforeProvisioningRule);
        b.put("after_provisioning_rule", r.afterProvisioningRule);
        b.put("account_schema_correlation_rule", r.accountSchemaCorrelationRule);
        b.put("account_schema_customization_rule", r.accountSchemaCustomizationRule);
        b.put("account_schema_creation_rule", r.accountSchemaCreationRule);
        b.put("account_schema_refresh_rule", r.accountSchemaRefreshRule);
        b.put("application_creation_rule", r.applicationCreationRule);
        b.put("account_schema_correlation_rule_id", r.accountSchemaCorrelationRuleId);
        b.put("account_schema_customization_rule_id", r.accountSchemaCustomizationRuleId);
        b.put("account_schema_creation_rule_id", r.accountSchemaCreationRuleId);
        b.put("account_schema_refresh_rule_id", r.accountSchemaRefreshRuleId);
        b.put("application_creation_rule_id", r.applicationCreationRuleId);
        b.put("score", r.score);
        b.put("authoritative", r.authoritative);
        b.put("case_insensitive", r.caseInsensitive);
        b.put("logical", r.logical);
        b.put("composite", r.composite);
        b.put("authentication_resource", r.authenticationResource);
        b.put("activity_enabled", r.activityEnabled);
        b.put("in_maintenance", r.inMaintenance);
        b.put("manages_other_apps", r.managesOtherApps);
        b.put("native_change_detection_enabled", r.nativeChangeDetectionEnabled);
        b.put("supports_provisioning", r.supportsProvisioning);
        b.put("supports_account_only", r.supportsAccountOnly);
        b.put("supports_additional_accounts", r.supportsAdditionalAccounts);
        b.put("supports_authenticate", r.supportsAuthenticate);
        b.put("supports_group_provisioning", r.supportsGroupProvisioning);
        b.put("supports_direct_permissions", r.supportsDirectPermissions);
        b.put("sync_provisioning", r.syncProvisioning);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("secondary_owners", r.secondaryOwnersJson);
        b.put("remediators", r.remediatorsJson);
        b.put("dependencies", r.dependenciesJson);
        b.put("schemas", r.schemasJson);
        b.put("descriptions", r.descriptionsJson);
        b.put("attributes", r.attributesJson);
        b.put("provisioning_config", r.provisioningConfig);
        b.put("account_correlation_config", r.accountCorrelationConfig);
        b.put("manager_correlation_rule", r.managerCorrelationRule);
        b.put("manager_correlation_filter", r.managerCorrelationFilter);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            // Additive migration for tables created before the rule-enrichment columns existed.
            // ADD COLUMN IF NOT EXISTS preserves existing rows (new columns default to NULL).
            st.execute("ALTER TABLE " + targetTable
                    + " ADD COLUMN IF NOT EXISTS account_schema_correlation_rule text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_customization_rule text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_creation_rule text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_refresh_rule text, "
                    + " ADD COLUMN IF NOT EXISTS application_creation_rule text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_correlation_rule_id text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_customization_rule_id text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_creation_rule_id text, "
                    + " ADD COLUMN IF NOT EXISTS account_schema_refresh_rule_id text, "
                    + " ADD COLUMN IF NOT EXISTS application_creation_rule_id text, "
                    + " ADD COLUMN IF NOT EXISTS provisioning_config text, "
                    + " ADD COLUMN IF NOT EXISTS account_correlation_config text, "
                    + " ADD COLUMN IF NOT EXISTS manager_correlation_rule text, "
                    + " ADD COLUMN IF NOT EXISTS manager_correlation_filter text");
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeApplicationRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalApplicationId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.description);
            ps.setString(i++, r.type);
            ps.setString(i++, r.connector);
            ps.setString(i++, r.featuresString);
            ps.setString(i++, r.profileClass);
            ps.setString(i++, r.proxiedName);
            ps.setString(i++, r.cluster);
            ps.setString(i++, r.icon);
            ps.setString(i++, r.aggregationTypes);
            ps.setString(i++, r.beforeProvisioningRule);
            ps.setString(i++, r.afterProvisioningRule);
            ps.setString(i++, r.accountSchemaCorrelationRule);
            ps.setString(i++, r.accountSchemaCustomizationRule);
            ps.setString(i++, r.accountSchemaCreationRule);
            ps.setString(i++, r.accountSchemaRefreshRule);
            ps.setString(i++, r.applicationCreationRule);
            ps.setString(i++, r.accountSchemaCorrelationRuleId);
            ps.setString(i++, r.accountSchemaCustomizationRuleId);
            ps.setString(i++, r.accountSchemaCreationRuleId);
            ps.setString(i++, r.accountSchemaRefreshRuleId);
            ps.setString(i++, r.applicationCreationRuleId);
            setInt(ps, i++, r.score);
            setBool(ps, i++, r.authoritative);
            setBool(ps, i++, r.caseInsensitive);
            setBool(ps, i++, r.logical);
            setBool(ps, i++, r.composite);
            setBool(ps, i++, r.authenticationResource);
            setBool(ps, i++, r.activityEnabled);
            setBool(ps, i++, r.inMaintenance);
            setBool(ps, i++, r.managesOtherApps);
            setBool(ps, i++, r.nativeChangeDetectionEnabled);
            setBool(ps, i++, r.supportsProvisioning);
            setBool(ps, i++, r.supportsAccountOnly);
            setBool(ps, i++, r.supportsAdditionalAccounts);
            setBool(ps, i++, r.supportsAuthenticate);
            setBool(ps, i++, r.supportsGroupProvisioning);
            setBool(ps, i++, r.supportsDirectPermissions);
            setBool(ps, i++, r.syncProvisioning);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.secondaryOwnersJson);
            ps.setString(i++, r.remediatorsJson);
            ps.setString(i++, r.dependenciesJson);
            ps.setString(i++, r.schemasJson);
            ps.setString(i++, r.descriptionsJson);
            ps.setString(i++, r.attributesJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            ps.setString(i++, r.provisioningConfig);
            ps.setString(i++, r.accountCorrelationConfig);
            ps.setString(i++, r.managerCorrelationRule);
            ps.setString(i++, r.managerCorrelationFilter);
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
