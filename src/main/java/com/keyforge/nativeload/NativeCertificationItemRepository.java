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
 * Plain-JDBC persistence for native CertificationItem rows (with folded decision/action) into
 * {@code <schema>.kf_certification_item} (current-state: idempotent upsert on the canonical UUID of the
 * item id; soft-delete via the shared sweeper). Business-content {@code record_hash} for change detection.
 */
public final class NativeCertificationItemRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String alterTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeCertificationItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_certification_item";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "certificationitemid uuid PRIMARY KEY, source_id text, certification_id text, "
                        + "entity_id text, identity text, type text, sub_type text, bundle text, "
                        + "bundle_assignment_id text, exception_application text, exception_attribute_name text, "
                        + "exception_attribute_value text, exception_permission_target text, "
                        + "exception_permission_right text, account_group text, phase text, summary_status text, "
                        + "completed timestamptz, last_decision timestamptz, expiration_date timestamptz, "
                        + "finished_date timestamptz, iiq_elevated_access boolean, reviewed boolean, "
                        + "delegated boolean, acted_upon boolean, historical boolean, expired boolean, "
                        + "target_id text, target_name text, short_description text, violation_summary text, "
                        + "application_names jsonb, classification_names jsonb, action_status text, "
                        + "action_decision_date timestamptz, action_decision_certification_id text, "
                        + "action_remediation_action text, action_actor_name text, action_actor_display_name text, "
                        + "action_comments text, action_completion_comments text, action_owner_name text, "
                        + "action_mitigation_expiration timestamptz, action_is_approved boolean, "
                        + "action_is_remediation boolean, action_is_mitigation boolean, action_is_delegation boolean, "
                        + "action_is_revoke_account boolean, action_is_auto_decision boolean, "
                        + "action_is_bulk_certified boolean, owner_id text, owner_name text, "
                        + "policy_violation_id text, role_assignment text, created_at timestamptz, "
                        + "modified_at timestamptz, record_hash text, source_system text, source_interface text, "
                        + "source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.alterTableSql =
                "ALTER TABLE " + targetTable
                        + " ADD COLUMN IF NOT EXISTS policy_violation_id text,"
                        + " ADD COLUMN IF NOT EXISTS role_assignment text";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "certificationitemid, source_id, certification_id, entity_id, identity, type, sub_type, "
                        + "bundle, bundle_assignment_id, exception_application, exception_attribute_name, "
                        + "exception_attribute_value, exception_permission_target, exception_permission_right, "
                        + "account_group, phase, summary_status, completed, last_decision, expiration_date, "
                        + "finished_date, iiq_elevated_access, reviewed, delegated, acted_upon, historical, expired, "
                        + "target_id, target_name, short_description, violation_summary, application_names, "
                        + "classification_names, action_status, action_decision_date, "
                        + "action_decision_certification_id, action_remediation_action, action_actor_name, "
                        + "action_actor_display_name, action_comments, action_completion_comments, action_owner_name, "
                        + "action_mitigation_expiration, action_is_approved, action_is_remediation, "
                        + "action_is_mitigation, action_is_delegation, action_is_revoke_account, "
                        + "action_is_auto_decision, action_is_bulk_certified, owner_id, owner_name, "
                        + "policy_violation_id, role_assignment, created_at, "
                        + "modified_at, record_hash, source_system, source_interface, source_object_type, "
                        + "extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (certificationitemid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, certification_id = EXCLUDED.certification_id, "
                        + "entity_id = EXCLUDED.entity_id, identity = EXCLUDED.identity, type = EXCLUDED.type, "
                        + "sub_type = EXCLUDED.sub_type, bundle = EXCLUDED.bundle, "
                        + "bundle_assignment_id = EXCLUDED.bundle_assignment_id, "
                        + "exception_application = EXCLUDED.exception_application, "
                        + "exception_attribute_name = EXCLUDED.exception_attribute_name, "
                        + "exception_attribute_value = EXCLUDED.exception_attribute_value, "
                        + "exception_permission_target = EXCLUDED.exception_permission_target, "
                        + "exception_permission_right = EXCLUDED.exception_permission_right, "
                        + "account_group = EXCLUDED.account_group, phase = EXCLUDED.phase, "
                        + "summary_status = EXCLUDED.summary_status, completed = EXCLUDED.completed, "
                        + "last_decision = EXCLUDED.last_decision, expiration_date = EXCLUDED.expiration_date, "
                        + "finished_date = EXCLUDED.finished_date, iiq_elevated_access = EXCLUDED.iiq_elevated_access, "
                        + "reviewed = EXCLUDED.reviewed, delegated = EXCLUDED.delegated, "
                        + "acted_upon = EXCLUDED.acted_upon, historical = EXCLUDED.historical, expired = EXCLUDED.expired, "
                        + "target_id = EXCLUDED.target_id, target_name = EXCLUDED.target_name, "
                        + "short_description = EXCLUDED.short_description, violation_summary = EXCLUDED.violation_summary, "
                        + "application_names = EXCLUDED.application_names, "
                        + "classification_names = EXCLUDED.classification_names, action_status = EXCLUDED.action_status, "
                        + "action_decision_date = EXCLUDED.action_decision_date, "
                        + "action_decision_certification_id = EXCLUDED.action_decision_certification_id, "
                        + "action_remediation_action = EXCLUDED.action_remediation_action, "
                        + "action_actor_name = EXCLUDED.action_actor_name, "
                        + "action_actor_display_name = EXCLUDED.action_actor_display_name, "
                        + "action_comments = EXCLUDED.action_comments, "
                        + "action_completion_comments = EXCLUDED.action_completion_comments, "
                        + "action_owner_name = EXCLUDED.action_owner_name, "
                        + "action_mitigation_expiration = EXCLUDED.action_mitigation_expiration, "
                        + "action_is_approved = EXCLUDED.action_is_approved, "
                        + "action_is_remediation = EXCLUDED.action_is_remediation, "
                        + "action_is_mitigation = EXCLUDED.action_is_mitigation, "
                        + "action_is_delegation = EXCLUDED.action_is_delegation, "
                        + "action_is_revoke_account = EXCLUDED.action_is_revoke_account, "
                        + "action_is_auto_decision = EXCLUDED.action_is_auto_decision, "
                        + "action_is_bulk_certified = EXCLUDED.action_is_bulk_certified, "
                        + "owner_id = EXCLUDED.owner_id, owner_name = EXCLUDED.owner_name, "
                        + "policy_violation_id = EXCLUDED.policy_violation_id, "
                        + "role_assignment = EXCLUDED.role_assignment, "
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

    public static String canonicalItemId(NativeCertificationItemRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-certification-item|" + r.sourceId);
        }
        return id;
    }

    public static String recordHash(NativeCertificationItemRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("certification_id", r.certificationId);
        b.put("entity_id", r.entityId);
        b.put("identity", r.identity);
        b.put("type", r.type);
        b.put("bundle", r.bundle);
        b.put("exception_application", r.exceptionApplication);
        b.put("exception_attribute_name", r.exceptionAttributeName);
        b.put("exception_attribute_value", r.exceptionAttributeValue);
        b.put("summary_status", r.summaryStatus);
        b.put("completed", r.completed);
        b.put("last_decision", r.lastDecision);
        b.put("action_status", r.actionStatus);
        b.put("action_decision_date", r.actionDecisionDate);
        b.put("action_actor_name", r.actionActorName);
        b.put("action_comments", r.actionComments);
        b.put("action_remediation_action", r.actionRemediationAction);
        b.put("iiq_elevated_access", r.iiqElevatedAccess);
        b.put("policy_violation_id", r.policyViolationId);
        b.put("role_assignment", r.roleAssignment);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            st.execute(alterTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeCertificationItemRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalItemId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.entityId);
            ps.setString(i++, r.identity);
            ps.setString(i++, r.type);
            ps.setString(i++, r.subType);
            ps.setString(i++, r.bundle);
            ps.setString(i++, r.bundleAssignmentId);
            ps.setString(i++, r.exceptionApplication);
            ps.setString(i++, r.exceptionAttributeName);
            ps.setString(i++, r.exceptionAttributeValue);
            ps.setString(i++, r.exceptionPermissionTarget);
            ps.setString(i++, r.exceptionPermissionRight);
            ps.setString(i++, r.accountGroup);
            ps.setString(i++, r.phase);
            ps.setString(i++, r.summaryStatus);
            setTs(ps, i++, r.completed);
            setTs(ps, i++, r.lastDecision);
            setTs(ps, i++, r.expirationDate);
            setTs(ps, i++, r.finishedDate);
            setBool(ps, i++, r.iiqElevatedAccess);
            setBool(ps, i++, r.reviewed);
            setBool(ps, i++, r.delegated);
            setBool(ps, i++, r.actedUpon);
            setBool(ps, i++, r.historical);
            setBool(ps, i++, r.expired);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.targetName);
            ps.setString(i++, r.shortDescription);
            ps.setString(i++, r.violationSummary);
            ps.setString(i++, r.applicationNamesJson);
            ps.setString(i++, r.classificationNamesJson);
            ps.setString(i++, r.actionStatus);
            setTs(ps, i++, r.actionDecisionDate);
            ps.setString(i++, r.actionDecisionCertificationId);
            ps.setString(i++, r.actionRemediationAction);
            ps.setString(i++, r.actionActorName);
            ps.setString(i++, r.actionActorDisplayName);
            ps.setString(i++, r.actionComments);
            ps.setString(i++, r.actionCompletionComments);
            ps.setString(i++, r.actionOwnerName);
            setTs(ps, i++, r.actionMitigationExpiration);
            setBool(ps, i++, r.actionIsApproved);
            setBool(ps, i++, r.actionIsRemediation);
            setBool(ps, i++, r.actionIsMitigation);
            setBool(ps, i++, r.actionIsDelegation);
            setBool(ps, i++, r.actionIsRevokeAccount);
            setBool(ps, i++, r.actionIsAutoDecision);
            setBool(ps, i++, r.actionIsBulkCertified);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.policyViolationId);
            ps.setString(i++, r.roleAssignment);
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
