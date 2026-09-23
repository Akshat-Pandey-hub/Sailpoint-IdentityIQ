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
 * Plain-JDBC persistence for native ProvisioningTransaction rows into {@code <schema>.kf_provisioning_txn}
 * (historical/transactional retention is not established by the native API, so this path upserts but does not
 * infer deletion from absence). Business-content {@code record_hash}
 * captures the mutable status/result so an update fires only when the source actually changed.
 */
public final class NativeProvisioningTxnRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeProvisioningTxnRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_provisioning_txn";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "provisioningtxnid uuid PRIMARY KEY, source_id text, name text, operation text, type text, "
                        + "status text, source text, integration text, forced boolean, identity_name text, "
                        + "identity_display_name text, application_name text, native_identity text, "
                        + "account_display_name text, certification_id text, certification_name text, "
                        + "access_request_id text, wait_work_item_id text, manual_work_item_id text, ticket_id text, "
                        + "retry_count integer, timed_out boolean, filtered boolean, retry_request_id text, "
                        + "last_retry timestamptz, plan_result_status text, "
                        + "plan_result_request_id text, plan_result_errors jsonb, account_request_operation text, "
                        + "request_id text, item_count integer, owner_id text, owner_name text, created_at timestamptz, "
                        + "modified_at timestamptz, record_hash text, source_system text, source_interface text, "
                        + "source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "provisioningtxnid, source_id, name, operation, type, status, source, integration, forced, "
                        + "identity_name, identity_display_name, application_name, native_identity, "
                        + "account_display_name, certification_id, certification_name, access_request_id, "
                        + "wait_work_item_id, manual_work_item_id, ticket_id, retry_count, timed_out, filtered, "
                        + "retry_request_id, last_retry, "
                        + "plan_result_status, plan_result_request_id, plan_result_errors, account_request_operation, "
                        + "request_id, item_count, owner_id, owner_name, created_at, modified_at, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, " + String.join(", ", java.util.Collections.nCopies(26, "?"))
                        + ", ?::jsonb, " + String.join(", ", java.util.Collections.nCopies(12, "?")) + ") "
                        + "ON CONFLICT (provisioningtxnid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, operation = EXCLUDED.operation, "
                        + "type = EXCLUDED.type, status = EXCLUDED.status, source = EXCLUDED.source, "
                        + "integration = EXCLUDED.integration, forced = EXCLUDED.forced, "
                        + "identity_name = EXCLUDED.identity_name, identity_display_name = EXCLUDED.identity_display_name, "
                        + "application_name = EXCLUDED.application_name, native_identity = EXCLUDED.native_identity, "
                        + "account_display_name = EXCLUDED.account_display_name, "
                        + "certification_id = EXCLUDED.certification_id, certification_name = EXCLUDED.certification_name, "
                        + "access_request_id = EXCLUDED.access_request_id, wait_work_item_id = EXCLUDED.wait_work_item_id, "
                        + "manual_work_item_id = EXCLUDED.manual_work_item_id, ticket_id = EXCLUDED.ticket_id, "
                        + "retry_count = EXCLUDED.retry_count, timed_out = EXCLUDED.timed_out, "
                        + "filtered = EXCLUDED.filtered, retry_request_id = EXCLUDED.retry_request_id, "
                        + "last_retry = EXCLUDED.last_retry, "
                        + "plan_result_status = EXCLUDED.plan_result_status, "
                        + "plan_result_request_id = EXCLUDED.plan_result_request_id, "
                        + "plan_result_errors = EXCLUDED.plan_result_errors, "
                        + "account_request_operation = EXCLUDED.account_request_operation, "
                        + "request_id = EXCLUDED.request_id, item_count = EXCLUDED.item_count, "
                        + "owner_id = EXCLUDED.owner_id, owner_name = EXCLUDED.owner_name, "
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

    public static String canonicalTxnId(NativeProvisioningTxnRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            if (r.sourceId == null || r.sourceId.isBlank()) {
                throw new IllegalArgumentException("ProvisioningTransaction source id is required for deterministic identity");
            }
            id = ParquetIds.deterministicUuid("native-provisioning-txn|" + r.sourceId);
        }
        return id;
    }

    public static String recordHash(NativeProvisioningTxnRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("operation", r.operation);
        b.put("type", r.type);
        b.put("status", r.status);
        b.put("source", r.source);
        b.put("integration", r.integration);
        b.put("forced", r.forced);
        b.put("identity_name", r.identityName);
        b.put("identity_display_name", r.identityDisplayName);
        b.put("application_name", r.applicationName);
        b.put("native_identity", r.nativeIdentity);
        b.put("account_display_name", r.accountDisplayName);
        b.put("certification_id", r.certificationId);
        b.put("certification_name", r.certificationName);
        b.put("access_request_id", r.accessRequestId);
        b.put("wait_work_item_id", r.waitWorkItemId);
        b.put("manual_work_item_id", r.manualWorkItemId);
        b.put("ticket_id", r.ticketId);
        b.put("retry_request_id", r.retryRequestId);
        b.put("retry_count", r.retryCount);
        b.put("timed_out", r.timedOut);
        b.put("filtered", r.filtered);
        b.put("last_retry", r.lastRetry);
        b.put("plan_result_status", r.planResultStatus);
        b.put("plan_result_request_id", r.planResultRequestId);
        b.put("plan_result_errors", r.planResultErrorsJson);
        b.put("account_request_operation", r.accountRequestOperation);
        b.put("request_id", r.requestId);
        b.put("item_count", r.itemCount);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS retry_request_id text");
            st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS last_retry timestamptz");
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeProvisioningTxnRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalTxnId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.operation);
            ps.setString(i++, r.type);
            ps.setString(i++, r.status);
            ps.setString(i++, r.source);
            ps.setString(i++, r.integration);
            setBool(ps, i++, r.forced);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.identityDisplayName);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.accountDisplayName);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.certificationName);
            ps.setString(i++, r.accessRequestId);
            ps.setString(i++, r.waitWorkItemId);
            ps.setString(i++, r.manualWorkItemId);
            ps.setString(i++, r.ticketId);
            setInt(ps, i++, r.retryCount);
            setBool(ps, i++, r.timedOut);
            setBool(ps, i++, r.filtered);
            ps.setString(i++, r.retryRequestId);
            setTs(ps, i++, r.lastRetry);
            ps.setString(i++, r.planResultStatus);
            ps.setString(i++, r.planResultRequestId);
            ps.setString(i++, r.planResultErrorsJson);
            ps.setString(i++, r.accountRequestOperation);
            ps.setString(i++, r.requestId);
            setInt(ps, i++, r.itemCount);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
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
