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
 * Plain-JDBC persistence for native IdentityEntitlement rows into {@code <schema>.kf_identity_entitlement}
 * — the current-state identity&nbsp;&harr;&nbsp;entitlement relationship with provenance.
 *
 * <p>Idempotent current-state: PK {@code entitlementid} is the deterministic canonical UUID of the
 * {@code IdentityEntitlement} id; every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row carries a
 * deterministic {@code record_hash} over its business fields for change detection. Deletions are handled by
 * the shared {@link com.keyforge.iiq.deletion.SoftDeleteSweeper} (this is current-state, not CEC).
 */
public final class NativeIdentityEntitlementRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "entitlementid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "identity_id text, "
                        + "identity_name text, "
                        + "application_id text, "
                        + "application_name text, "
                        + "native_identity text, "
                        + "instance text, "
                        + "attribute_name text, "
                        + "attribute_value text, "
                        + "value_list jsonb, "
                        + "type text, "
                        + "display_name text, "
                        + "annotation text, "
                        + "assigned boolean, "
                        + "granted_by_role boolean, "
                        + "allowed boolean, "
                        + "connected boolean, "
                        + "aggregation_state text, "
                        + "source text, "
                        + "source_object text, "
                        + "assigner text, "
                        + "assignment_id text, "
                        + "assignment_note text, "
                        + "source_assignable_roles text, "
                        + "source_detected_roles text, "
                        + "certification_item_id text, "
                        + "pending_certification_item_id text, "
                        + "request_item_id text, "
                        + "pending_request_item_id text, "
                        + "start_date timestamptz, "
                        + "end_date timestamptz, "
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
                        + "entitlementid, source_id, identity_id, identity_name, application_id, application_name, "
                        + "native_identity, instance, attribute_name, attribute_value, value_list, type, "
                        + "display_name, annotation, assigned, granted_by_role, allowed, connected, "
                        + "aggregation_state, source, source_object, assigner, assignment_id, assignment_note, "
                        + "source_assignable_roles, source_detected_roles, certification_item_id, "
                        + "pending_certification_item_id, request_item_id, pending_request_item_id, start_date, "
                        + "end_date, created_at, modified_at, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (entitlementid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, identity_id = EXCLUDED.identity_id, "
                        + "identity_name = EXCLUDED.identity_name, application_id = EXCLUDED.application_id, "
                        + "application_name = EXCLUDED.application_name, native_identity = EXCLUDED.native_identity, "
                        + "instance = EXCLUDED.instance, attribute_name = EXCLUDED.attribute_name, "
                        + "attribute_value = EXCLUDED.attribute_value, value_list = EXCLUDED.value_list, "
                        + "type = EXCLUDED.type, display_name = EXCLUDED.display_name, "
                        + "annotation = EXCLUDED.annotation, assigned = EXCLUDED.assigned, "
                        + "granted_by_role = EXCLUDED.granted_by_role, allowed = EXCLUDED.allowed, "
                        + "connected = EXCLUDED.connected, aggregation_state = EXCLUDED.aggregation_state, "
                        + "source = EXCLUDED.source, source_object = EXCLUDED.source_object, "
                        + "assigner = EXCLUDED.assigner, assignment_id = EXCLUDED.assignment_id, "
                        + "assignment_note = EXCLUDED.assignment_note, "
                        + "source_assignable_roles = EXCLUDED.source_assignable_roles, "
                        + "source_detected_roles = EXCLUDED.source_detected_roles, "
                        + "certification_item_id = EXCLUDED.certification_item_id, "
                        + "pending_certification_item_id = EXCLUDED.pending_certification_item_id, "
                        + "request_item_id = EXCLUDED.request_item_id, "
                        + "pending_request_item_id = EXCLUDED.pending_request_item_id, "
                        + "start_date = EXCLUDED.start_date, end_date = EXCLUDED.end_date, "
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

    /** Deterministic PK: canonical UUID of the IdentityEntitlement id, with a stable composite fallback. */
    public static String canonicalEntitlementId(NativeIdentityEntitlementRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-identity-entitlement|"
                    + r.identityId + "|" + r.applicationName + "|" + r.nativeIdentity + "|"
                    + r.attributeName + "|" + r.attributeValue);
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeIdentityEntitlementRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("identity_id", r.identityId);
        b.put("application_name", r.applicationName);
        b.put("native_identity", r.nativeIdentity);
        b.put("instance", r.instance);
        b.put("attribute_name", r.attributeName);
        b.put("attribute_value", r.attributeValue);
        b.put("value_list", r.valueListJson);
        b.put("type", r.type);
        b.put("annotation", r.annotation);
        b.put("assigned", r.assigned);
        b.put("granted_by_role", r.grantedByRole);
        b.put("allowed", r.allowed);
        b.put("connected", r.connected);
        b.put("aggregation_state", r.aggregationState);
        b.put("source", r.source);
        b.put("source_object", r.sourceObject);
        b.put("assigner", r.assigner);
        b.put("assignment_id", r.assignmentId);
        b.put("assignment_note", r.assignmentNote);
        b.put("source_assignable_roles", r.sourceAssignableRoles);
        b.put("source_detected_roles", r.sourceDetectedRoles);
        b.put("certification_item_id", r.certificationItemId);
        b.put("pending_certification_item_id", r.pendingCertificationItemId);
        b.put("request_item_id", r.requestItemId);
        b.put("pending_request_item_id", r.pendingRequestItemId);
        b.put("start_date", r.startDate);
        b.put("end_date", r.endDate);
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

    public UpsertOutcome upsert(Connection conn, NativeIdentityEntitlementRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalEntitlementId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.identityId);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.applicationId);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.attributeName);
            ps.setString(i++, r.attributeValue);
            ps.setString(i++, r.valueListJson);
            ps.setString(i++, r.type);
            ps.setString(i++, r.displayName);
            ps.setString(i++, r.annotation);
            setBool(ps, i++, r.assigned);
            setBool(ps, i++, r.grantedByRole);
            setBool(ps, i++, r.allowed);
            setBool(ps, i++, r.connected);
            ps.setString(i++, r.aggregationState);
            ps.setString(i++, r.source);
            ps.setString(i++, r.sourceObject);
            ps.setString(i++, r.assigner);
            ps.setString(i++, r.assignmentId);
            ps.setString(i++, r.assignmentNote);
            ps.setString(i++, r.sourceAssignableRoles);
            ps.setString(i++, r.sourceDetectedRoles);
            ps.setString(i++, r.certificationItemId);
            ps.setString(i++, r.pendingCertificationItemId);
            ps.setString(i++, r.requestItemId);
            ps.setString(i++, r.pendingRequestItemId);
            setTs(ps, i++, r.startDate);
            setTs(ps, i++, r.endDate);
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
