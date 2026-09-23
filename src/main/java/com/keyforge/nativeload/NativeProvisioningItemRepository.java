package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.parquet.ParquetIds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-JDBC persistence for native provisioning items into {@code <schema>.kf_provisioning_item}
 * (derived rows with no independent native source identity). PK is deterministic over the parent transaction,
 * item content and source position; historical rows are retained because absence does not prove deletion.
 */
public final class NativeProvisioningItemRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeProvisioningItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_provisioning_item";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "provisioningitemid uuid PRIMARY KEY, txn_source_id text, identity_name text, "
                        + "item_type text, operation text, application_name text, native_identity text, instance text, "
                        + "account_operation text, name text, value text, value_json jsonb, assignment_id text, assignment boolean, "
                        + "permission_target text, permission_rights text, request_id text, item_index integer, "
                        + "record_hash text, source_system text, source_interface text, source_object_type text, "
                        + "src_object_id text, src_natural_key text, "
                        + "extraction_run_id text, extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "provisioningitemid, txn_source_id, identity_name, item_type, operation, application_name, "
                        + "native_identity, instance, account_operation, name, value, value_json, assignment_id, assignment, "
                        + "permission_target, permission_rights, request_id, item_index, record_hash, source_system, "
                        + "source_interface, source_object_type, src_object_id, src_natural_key, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provisioningitemid) DO UPDATE SET "
                        + "txn_source_id = EXCLUDED.txn_source_id, identity_name = EXCLUDED.identity_name, "
                        + "item_type = EXCLUDED.item_type, operation = EXCLUDED.operation, "
                        + "application_name = EXCLUDED.application_name, native_identity = EXCLUDED.native_identity, "
                        + "instance = EXCLUDED.instance, account_operation = EXCLUDED.account_operation, "
                        + "name = EXCLUDED.name, value = EXCLUDED.value, value_json = EXCLUDED.value_json, "
                        + "assignment_id = EXCLUDED.assignment_id, "
                        + "assignment = EXCLUDED.assignment, permission_target = EXCLUDED.permission_target, "
                        + "permission_rights = EXCLUDED.permission_rights, request_id = EXCLUDED.request_id, "
                        + "item_index = EXCLUDED.item_index, record_hash = EXCLUDED.record_hash, "
                        + "source_system = EXCLUDED.source_system, source_interface = EXCLUDED.source_interface, "
                        + "source_object_type = EXCLUDED.source_object_type, "
                        + "src_object_id = EXCLUDED.src_object_id, src_natural_key = EXCLUDED.src_natural_key, "
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

    /** Deterministic PK from the parent txn id + item content (never random). */
    public static String canonicalItemId(NativeProvisioningItemRecord r) {
        return ParquetIds.deterministicUuid("native-provisioning-item|" + r.txnSourceId + "|" + r.itemType
                + "|" + r.operation + "|" + r.name + "|" + r.valueJson + "|" + r.value + "|" + r.itemIndex);
    }

    public static String recordHash(NativeProvisioningItemRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("txn_source_id", r.txnSourceId);
        b.put("identity_name", r.identityName);
        b.put("item_type", r.itemType);
        b.put("operation", r.operation);
        b.put("application_name", r.applicationName);
        b.put("native_identity", r.nativeIdentity);
        b.put("account_operation", r.accountOperation);
        b.put("name", r.name);
        b.put("value", r.value);
        b.put("value_json", r.valueJson);
        b.put("assignment_id", r.assignmentId);
        b.put("assignment", r.assignment);
        b.put("permission_target", r.permissionTarget);
        b.put("permission_rights", r.permissionRights);
        b.put("request_id", r.requestId);
        b.put("item_index", r.itemIndex);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS value_json jsonb");
            st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS src_object_id text");
            st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS src_natural_key text");
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeProvisioningItemRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalItemId(r));
            ps.setString(i++, r.txnSourceId);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.itemType);
            ps.setString(i++, r.operation);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.accountOperation);
            ps.setString(i++, r.name);
            ps.setString(i++, r.value);
            ps.setString(i++, r.valueJson);
            ps.setString(i++, r.assignmentId);
            setBool(ps, i++, r.assignment);
            ps.setString(i++, r.permissionTarget);
            ps.setString(i++, r.permissionRights);
            ps.setString(i++, r.requestId);
            setInt(ps, i++, r.itemIndex);
            ps.setString(i++, recordHash(r));
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.srcObjectType);
            // Derived items have no independent IIQ object ID: reference the parent transaction
            // and use a stable item discriminator as their natural key.
            ps.setString(i++, r.txnSourceId);
            ps.setString(i++, derivedNaturalKey(r));
            ps.setString(i, r.extractionRunId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    static String derivedNaturalKey(NativeProvisioningItemRecord r) {
        return r.txnSourceId + "|" + r.itemType + "|" + r.itemIndex;
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
}
