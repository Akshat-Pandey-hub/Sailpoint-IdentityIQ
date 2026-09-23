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
 * Plain-JDBC persistence for native CertificationEntity rows into {@code <schema>.kf_certification_entity}
 * (current-state: idempotent upsert on the deterministic canonical UUID of the CertificationEntity id;
 * soft-delete via the shared sweeper). Business-content {@code record_hash} for change detection.
 */
public final class NativeCertificationEntityRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeCertificationEntityRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_certification_entity";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "certificationentityid uuid PRIMARY KEY, source_id text, certification_id text, "
                        + "identity text, application text, native_identity text, account_group text, "
                        + "first_name text, last_name text, full_name text, reference_attribute text, "
                        + "schema_object_type text, snapshot_id text, pending_certification text, type text, "
                        + "summary_status text, entity_delegated boolean, entity_delegation_status text, "
                        + "composite_score integer, target_id text, target_name text, target_display_name text, "
                        + "completed timestamptz, created_at timestamptz, modified_at timestamptz, "
                        + "owner_id text, owner_name text, action_status text, action_decision_date timestamptz, "
                        + "action_remediation_action text, action_actor_name text, action_actor_display_name text, "
                        + "action_comments text, record_hash text, source_system text, source_interface text, "
                        + "source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "certificationentityid, source_id, certification_id, identity, application, native_identity, "
                        + "account_group, first_name, last_name, full_name, reference_attribute, schema_object_type, "
                        + "snapshot_id, pending_certification, type, summary_status, entity_delegated, "
                        + "entity_delegation_status, composite_score, target_id, target_name, target_display_name, "
                        + "completed, created_at, modified_at, owner_id, owner_name, action_status, "
                        + "action_decision_date, action_remediation_action, action_actor_name, "
                        + "action_actor_display_name, action_comments, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (certificationentityid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, certification_id = EXCLUDED.certification_id, "
                        + "identity = EXCLUDED.identity, application = EXCLUDED.application, "
                        + "native_identity = EXCLUDED.native_identity, account_group = EXCLUDED.account_group, "
                        + "first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, "
                        + "full_name = EXCLUDED.full_name, reference_attribute = EXCLUDED.reference_attribute, "
                        + "schema_object_type = EXCLUDED.schema_object_type, snapshot_id = EXCLUDED.snapshot_id, "
                        + "pending_certification = EXCLUDED.pending_certification, type = EXCLUDED.type, "
                        + "summary_status = EXCLUDED.summary_status, entity_delegated = EXCLUDED.entity_delegated, "
                        + "entity_delegation_status = EXCLUDED.entity_delegation_status, "
                        + "composite_score = EXCLUDED.composite_score, target_id = EXCLUDED.target_id, "
                        + "target_name = EXCLUDED.target_name, target_display_name = EXCLUDED.target_display_name, "
                        + "completed = EXCLUDED.completed, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, action_status = EXCLUDED.action_status, "
                        + "action_decision_date = EXCLUDED.action_decision_date, "
                        + "action_remediation_action = EXCLUDED.action_remediation_action, "
                        + "action_actor_name = EXCLUDED.action_actor_name, "
                        + "action_actor_display_name = EXCLUDED.action_actor_display_name, "
                        + "action_comments = EXCLUDED.action_comments, record_hash = EXCLUDED.record_hash, "
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

    public static String canonicalCertificationEntityId(NativeCertificationEntityRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-certification-entity|"
                    + (r.sourceId == null ? r.identity : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativeCertificationEntityRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("certification_id", r.certificationId);
        b.put("identity", r.identity);
        b.put("application", r.application);
        b.put("native_identity", r.nativeIdentity);
        b.put("type", r.type);
        b.put("summary_status", r.summaryStatus);
        b.put("completed", r.completed);
        b.put("action_status", r.actionStatus);
        b.put("action_decision_date", r.actionDecisionDate);
        b.put("action_actor_name", r.actionActorName);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeCertificationEntityRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalCertificationEntityId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.identity);
            ps.setString(i++, r.application);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.accountGroup);
            ps.setString(i++, r.firstName);
            ps.setString(i++, r.lastName);
            ps.setString(i++, r.fullName);
            ps.setString(i++, r.referenceAttribute);
            ps.setString(i++, r.schemaObjectType);
            ps.setString(i++, r.snapshotId);
            ps.setString(i++, r.pendingCertification);
            ps.setString(i++, r.type);
            ps.setString(i++, r.summaryStatus);
            setBool(ps, i++, r.entityDelegated);
            ps.setString(i++, r.entityDelegationStatus);
            setInt(ps, i++, r.compositeScore);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.targetName);
            ps.setString(i++, r.targetDisplayName);
            setTs(ps, i++, r.completed);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.actionStatus);
            setTs(ps, i++, r.actionDecisionDate);
            ps.setString(i++, r.actionRemediationAction);
            ps.setString(i++, r.actionActorName);
            ps.setString(i++, r.actionActorDisplayName);
            ps.setString(i++, r.actionComments);
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
