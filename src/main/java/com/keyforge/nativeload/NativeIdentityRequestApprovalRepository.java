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
 * Plain-JDBC persistence for native IdentityRequest approval summaries into
 * {@code <schema>.kf_identity_request_approval} (current-state: upsert + soft-delete sweep). Approvals carry
 * no independent native identity, so the PK is deterministic over the parent request, work item, owner and
 * source position.
 */
public final class NativeIdentityRequestApprovalRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityRequestApprovalRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_request_approval";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "identityrequestapprovalid uuid PRIMARY KEY, request_source_id text, request_name text, "
                        + "work_item_id text, work_item_type text, owner text, owner_id text, completer text, "
                        + "approved boolean, state text, state_key text, type_key text, start_date timestamptz, "
                        + "end_date timestamptz, approval_item_count integer, approval_index integer, "
                        + "comments jsonb, sign_off jsonb, record_hash text, source_system text, "
                        + "source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "identityrequestapprovalid, request_source_id, request_name, work_item_id, "
                        + "work_item_type, owner, owner_id, completer, approved, state, state_key, type_key, "
                        + "start_date, end_date, approval_item_count, approval_index, comments, sign_off, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, " + String.join(", ", java.util.Collections.nCopies(15, "?"))
                        + ", ?::jsonb, ?::jsonb, " + String.join(", ", java.util.Collections.nCopies(5, "?")) + ") "
                        + "ON CONFLICT (identityrequestapprovalid) DO UPDATE SET "
                        + "request_source_id = EXCLUDED.request_source_id, request_name = EXCLUDED.request_name, "
                        + "work_item_id = EXCLUDED.work_item_id, work_item_type = EXCLUDED.work_item_type, "
                        + "owner = EXCLUDED.owner, owner_id = EXCLUDED.owner_id, completer = EXCLUDED.completer, "
                        + "approved = EXCLUDED.approved, state = EXCLUDED.state, state_key = EXCLUDED.state_key, "
                        + "type_key = EXCLUDED.type_key, start_date = EXCLUDED.start_date, "
                        + "end_date = EXCLUDED.end_date, approval_item_count = EXCLUDED.approval_item_count, "
                        + "approval_index = EXCLUDED.approval_index, comments = EXCLUDED.comments, "
                        + "sign_off = EXCLUDED.sign_off, record_hash = EXCLUDED.record_hash, "
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

    /** Deterministic PK over the parent request, work item, owner and source position (never random). */
    public static String canonicalApprovalId(NativeIdentityRequestApprovalRecord r) {
        return ParquetIds.deterministicUuid("native-identity-request-approval|" + r.requestSourceId + "|"
                + r.workItemId + "|" + r.owner + "|" + r.approvalIndex);
    }

    public static String recordHash(NativeIdentityRequestApprovalRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("request_source_id", r.requestSourceId);
        b.put("request_name", r.requestName);
        b.put("work_item_id", r.workItemId);
        b.put("work_item_type", r.workItemType);
        b.put("owner", r.owner);
        b.put("owner_id", r.ownerId);
        b.put("completer", r.completer);
        b.put("approved", r.approved);
        b.put("state", r.state);
        b.put("state_key", r.stateKey);
        b.put("type_key", r.typeKey);
        b.put("start_date", r.startDate);
        b.put("end_date", r.endDate);
        b.put("approval_item_count", r.approvalItemCount);
        b.put("approval_index", r.approvalIndex);
        b.put("comments", r.commentsJson);
        b.put("sign_off", r.signOffJson);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeIdentityRequestApprovalRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalApprovalId(r));
            ps.setString(i++, r.requestSourceId);
            ps.setString(i++, r.requestName);
            ps.setString(i++, r.workItemId);
            ps.setString(i++, r.workItemType);
            ps.setString(i++, r.owner);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.completer);
            setBool(ps, i++, r.approved);
            ps.setString(i++, r.state);
            ps.setString(i++, r.stateKey);
            ps.setString(i++, r.typeKey);
            setTs(ps, i++, r.startDate);
            setTs(ps, i++, r.endDate);
            setInt(ps, i++, r.approvalItemCount);
            setInt(ps, i++, r.approvalIndex);
            ps.setString(i++, r.commentsJson);
            ps.setString(i++, r.signOffJson);
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
