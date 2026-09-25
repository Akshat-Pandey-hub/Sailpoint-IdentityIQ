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
 * Plain-JDBC persistence for native Role (Bundle) rows into {@code <schema>.kf_role} (HLD table name;
 * REST PK column {@code roleid} mirrored so the schemas join). Native-shaped: rich scalar/flag columns
 * + {@code jsonb} for descriptions/attributes.
 *
 * <p>Idempotent: PK {@code roleid} is the deterministic canonical UUID of the Bundle id (same
 * derivation as the REST path); every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row
 * carries a deterministic {@code record_hash} over its business fields for change detection.
 */
public final class NativeRoleRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String alterTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeRoleRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_role";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "roleid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "display_name text, "
                        + "displayable_name text, "
                        + "full_name text, "
                        + "description text, "
                        + "type text, "
                        + "assignment_id text, "
                        + "activity_enabled boolean, "
                        + "allow_duplicate_accounts boolean, "
                        + "allow_multiple_assignments boolean, "
                        + "auto_promotion boolean, "
                        + "differencable boolean, "
                        + "iiq_elevated_access boolean, "
                        + "merge_templates boolean, "
                        + "or_profiles boolean, "
                        + "pending_delete boolean, "
                        + "has_selector boolean, "
                        + "risk_score_weight integer, "
                        + "owner_id text, "
                        + "owner_name text, "
                        + "activation_date timestamptz, "
                        + "deactivation_date timestamptz, "
                        + "descriptions jsonb, "
                        + "attributes jsonb, "
                        + "role_type_definition text, "
                        + "applications text, "
                        + "monitored_applications text, "
                        + "scorecard text, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.alterTableSql =
                "ALTER TABLE " + targetTable
                        + " ADD COLUMN IF NOT EXISTS role_type_definition text,"
                        + " ADD COLUMN IF NOT EXISTS applications text,"
                        + " ADD COLUMN IF NOT EXISTS monitored_applications text,"
                        + " ADD COLUMN IF NOT EXISTS scorecard text";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "roleid, source_id, name, display_name, displayable_name, full_name, description, type, "
                        + "assignment_id, activity_enabled, allow_duplicate_accounts, allow_multiple_assignments, "
                        + "auto_promotion, differencable, iiq_elevated_access, merge_templates, or_profiles, "
                        + "pending_delete, has_selector, risk_score_weight, owner_id, owner_name, activation_date, "
                        + "deactivation_date, descriptions, attributes, "
                        + "role_type_definition, applications, monitored_applications, scorecard, "
                        + "created_at, modified_at, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (roleid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, "
                        + "display_name = EXCLUDED.display_name, displayable_name = EXCLUDED.displayable_name, "
                        + "full_name = EXCLUDED.full_name, description = EXCLUDED.description, type = EXCLUDED.type, "
                        + "assignment_id = EXCLUDED.assignment_id, activity_enabled = EXCLUDED.activity_enabled, "
                        + "allow_duplicate_accounts = EXCLUDED.allow_duplicate_accounts, "
                        + "allow_multiple_assignments = EXCLUDED.allow_multiple_assignments, "
                        + "auto_promotion = EXCLUDED.auto_promotion, differencable = EXCLUDED.differencable, "
                        + "iiq_elevated_access = EXCLUDED.iiq_elevated_access, "
                        + "merge_templates = EXCLUDED.merge_templates, or_profiles = EXCLUDED.or_profiles, "
                        + "pending_delete = EXCLUDED.pending_delete, has_selector = EXCLUDED.has_selector, "
                        + "risk_score_weight = EXCLUDED.risk_score_weight, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, activation_date = EXCLUDED.activation_date, "
                        + "deactivation_date = EXCLUDED.deactivation_date, descriptions = EXCLUDED.descriptions, "
                        + "attributes = EXCLUDED.attributes, "
                        + "role_type_definition = EXCLUDED.role_type_definition, "
                        + "applications = EXCLUDED.applications, "
                        + "monitored_applications = EXCLUDED.monitored_applications, "
                        + "scorecard = EXCLUDED.scorecard, "
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
    public static String canonicalRoleId(NativeRoleRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-role|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeRoleRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("display_name", r.displayName);
        b.put("displayable_name", r.displayableName);
        b.put("full_name", r.fullName);
        b.put("description", r.description);
        b.put("type", r.type);
        b.put("assignment_id", r.assignmentId);
        b.put("activity_enabled", r.activityEnabled);
        b.put("allow_duplicate_accounts", r.allowDuplicateAccounts);
        b.put("allow_multiple_assignments", r.allowMultipleAssignments);
        b.put("auto_promotion", r.autoPromotion);
        b.put("differencable", r.differencable);
        b.put("iiq_elevated_access", r.iiqElevatedAccess);
        b.put("merge_templates", r.mergeTemplates);
        b.put("or_profiles", r.orProfiles);
        b.put("pending_delete", r.pendingDelete);
        b.put("has_selector", r.hasSelector);
        b.put("risk_score_weight", r.riskScoreWeight);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("activation_date", r.activationDate);
        b.put("deactivation_date", r.deactivationDate);
        b.put("descriptions", r.descriptionsJson);
        b.put("attributes", r.attributesJson);
        b.put("role_type_definition", r.roleTypeDefinition);
        b.put("applications", r.applicationsJson);
        b.put("monitored_applications", r.monitoredApplicationsJson);
        b.put("scorecard", r.scorecard);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            st.execute(alterTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeRoleRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalRoleId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.displayableName);
            ps.setString(i++, r.fullName);
            ps.setString(i++, r.description);
            ps.setString(i++, r.type);
            ps.setString(i++, r.assignmentId);
            setBool(ps, i++, r.activityEnabled);
            setBool(ps, i++, r.allowDuplicateAccounts);
            setBool(ps, i++, r.allowMultipleAssignments);
            setBool(ps, i++, r.autoPromotion);
            setBool(ps, i++, r.differencable);
            setBool(ps, i++, r.iiqElevatedAccess);
            setBool(ps, i++, r.mergeTemplates);
            setBool(ps, i++, r.orProfiles);
            setBool(ps, i++, r.pendingDelete);
            setBool(ps, i++, r.hasSelector);
            setInt(ps, i++, r.riskScoreWeight);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            setTs(ps, i++, r.activationDate);
            setTs(ps, i++, r.deactivationDate);
            ps.setString(i++, r.descriptionsJson);
            ps.setString(i++, r.attributesJson);
            ps.setString(i++, r.roleTypeDefinition);
            ps.setString(i++, r.applicationsJson);
            ps.setString(i++, r.monitoredApplicationsJson);
            ps.setString(i++, r.scorecard);
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
