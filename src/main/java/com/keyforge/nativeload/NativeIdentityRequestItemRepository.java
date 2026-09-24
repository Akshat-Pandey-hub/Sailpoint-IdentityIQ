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
 * Plain-JDBC persistence for native IdentityRequestItem rows into {@code <schema>.kf_identity_request_item}
 * (current-state: upsert + soft-delete sweep). PK is the item's own IIQ id when present, otherwise a
 * deterministic fallback over the item source id.
 */
public final class NativeIdentityRequestItemRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityRequestItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_request_item";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "identityrequestitemid uuid PRIMARY KEY, source_id text, request_source_id text, "
                        + "request_name text, application text, attribute_name text, attribute_value text, "
                        + "operation text, managed_attribute_type text, assignment_id text, native_identity text, "
                        + "instance text, approver_name text, approval_state text, approved boolean, "
                        + "approval_complete boolean, rejected boolean, provisioning_state text, "
                        + "provisioning_engine text, provisioning_request_id text, provisioning_complete boolean, "
                        + "provisioning_failed boolean, compilation_status text, owner_name text, "
                        + "requester_comments text, expansion boolean, expansion_cause text, expansion_info text, "
                        + "retries integer, iiq boolean, start_date timestamptz, end_date timestamptz, "
                        + "created_at timestamptz, modified_at timestamptz, record_hash text, source_system text, "
                        + "source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "identityrequestitemid, source_id, request_source_id, request_name, application, "
                        + "attribute_name, attribute_value, operation, managed_attribute_type, assignment_id, "
                        + "native_identity, instance, approver_name, approval_state, approved, approval_complete, "
                        + "rejected, provisioning_state, provisioning_engine, provisioning_request_id, "
                        + "provisioning_complete, provisioning_failed, compilation_status, owner_name, "
                        + "requester_comments, expansion, expansion_cause, expansion_info, retries, iiq, "
                        + "start_date, end_date, created_at, modified_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, " + String.join(", ", java.util.Collections.nCopies(38, "?")) + ") "
                        + "ON CONFLICT (identityrequestitemid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, request_source_id = EXCLUDED.request_source_id, "
                        + "request_name = EXCLUDED.request_name, application = EXCLUDED.application, "
                        + "attribute_name = EXCLUDED.attribute_name, attribute_value = EXCLUDED.attribute_value, "
                        + "operation = EXCLUDED.operation, managed_attribute_type = EXCLUDED.managed_attribute_type, "
                        + "assignment_id = EXCLUDED.assignment_id, native_identity = EXCLUDED.native_identity, "
                        + "instance = EXCLUDED.instance, approver_name = EXCLUDED.approver_name, "
                        + "approval_state = EXCLUDED.approval_state, approved = EXCLUDED.approved, "
                        + "approval_complete = EXCLUDED.approval_complete, rejected = EXCLUDED.rejected, "
                        + "provisioning_state = EXCLUDED.provisioning_state, "
                        + "provisioning_engine = EXCLUDED.provisioning_engine, "
                        + "provisioning_request_id = EXCLUDED.provisioning_request_id, "
                        + "provisioning_complete = EXCLUDED.provisioning_complete, "
                        + "provisioning_failed = EXCLUDED.provisioning_failed, "
                        + "compilation_status = EXCLUDED.compilation_status, owner_name = EXCLUDED.owner_name, "
                        + "requester_comments = EXCLUDED.requester_comments, expansion = EXCLUDED.expansion, "
                        + "expansion_cause = EXCLUDED.expansion_cause, expansion_info = EXCLUDED.expansion_info, "
                        + "retries = EXCLUDED.retries, iiq = EXCLUDED.iiq, start_date = EXCLUDED.start_date, "
                        + "end_date = EXCLUDED.end_date, created_at = EXCLUDED.created_at, "
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

    String upsertSql() {
        return upsertSql;
    }

    /** PK from the item's own IIQ id when present, else a deterministic fallback (never random). */
    public static String canonicalItemId(NativeIdentityRequestItemRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-identity-request-item|" + r.sourceId);
        }
        return id;
    }

    public static String recordHash(NativeIdentityRequestItemRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("request_source_id", r.requestSourceId);
        b.put("request_name", r.requestName);
        b.put("application", r.application);
        b.put("attribute_name", r.attributeName);
        b.put("attribute_value", r.attributeValue);
        b.put("operation", r.operation);
        b.put("managed_attribute_type", r.managedAttributeType);
        b.put("assignment_id", r.assignmentId);
        b.put("native_identity", r.nativeIdentity);
        b.put("instance", r.instance);
        b.put("approver_name", r.approverName);
        b.put("approval_state", r.approvalState);
        b.put("approved", r.approved);
        b.put("approval_complete", r.approvalComplete);
        b.put("rejected", r.rejected);
        b.put("provisioning_state", r.provisioningState);
        b.put("provisioning_engine", r.provisioningEngine);
        b.put("provisioning_request_id", r.provisioningRequestId);
        b.put("provisioning_complete", r.provisioningComplete);
        b.put("provisioning_failed", r.provisioningFailed);
        b.put("compilation_status", r.compilationStatus);
        b.put("owner_name", r.ownerName);
        b.put("requester_comments", r.requesterComments);
        b.put("expansion", r.expansion);
        b.put("expansion_cause", r.expansionCause);
        b.put("expansion_info", r.expansionInfo);
        b.put("retries", r.retries);
        b.put("iiq", r.iiq);
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

    public UpsertOutcome upsert(Connection conn, NativeIdentityRequestItemRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalItemId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.requestSourceId);
            ps.setString(i++, r.requestName);
            ps.setString(i++, r.application);
            ps.setString(i++, r.attributeName);
            ps.setString(i++, r.attributeValue);
            ps.setString(i++, r.operation);
            ps.setString(i++, r.managedAttributeType);
            ps.setString(i++, r.assignmentId);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.approverName);
            ps.setString(i++, r.approvalState);
            setBool(ps, i++, r.approved);
            setBool(ps, i++, r.approvalComplete);
            setBool(ps, i++, r.rejected);
            ps.setString(i++, r.provisioningState);
            ps.setString(i++, r.provisioningEngine);
            ps.setString(i++, r.provisioningRequestId);
            setBool(ps, i++, r.provisioningComplete);
            setBool(ps, i++, r.provisioningFailed);
            ps.setString(i++, r.compilationStatus);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.requesterComments);
            setBool(ps, i++, r.expansion);
            ps.setString(i++, r.expansionCause);
            ps.setString(i++, r.expansionInfo);
            setInt(ps, i++, r.retries);
            setBool(ps, i++, r.iiq);
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
