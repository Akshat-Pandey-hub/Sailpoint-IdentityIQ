package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.parquet.ParquetIds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APPEND-ONLY persistence for native {@code HistoricalRoleEvent} rows into
 * {@code <schema>.kf_access_hist_role_event}. Immutable access-history events: {@code INSERT … ON
 * CONFLICT (hist_event_id) DO NOTHING}. Event type/category/source are the source {@code name()} values;
 * {@code event_detail_json} is the raw source payload (text). PK = canonical UUID of the source id.
 */
public final class NativeHistRoleEventRepository implements NativeAccessHistoryRepo {

    static final String SRC_OBJECT_TYPE = "sailpoint.object.accesshistory.HistoricalRoleEvent";

    private final String extractionRunId;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    public NativeHistRoleEventRepository(String schema, String extractionRunId) {
        String s = SchemaName.validate(schema);
        this.extractionRunId = extractionRunId;
        this.targetTable = s + ".kf_access_hist_role_event";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + s;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "hist_event_id uuid PRIMARY KEY, source_id text, name text, entity_id text, "
                        + "entity_name text, defined_entity_name text, event_type text, event_category text, "
                        + "event_source_type text, event_date timestamptz, event_detail_json text, "
                        + "prev_capture_id text, capture_id text, audit_event_id text, property_name text, "
                        + "old_value text, new_value text, account_id text, acct_app_id text, acct_app_name text, "
                        + "acct_app_instance text, acct_display_name text, acct_native_id text, "
                        + "pending_request_item_id text, request_item_id text, identity_request_id text, "
                        + "identity_entitlement_id text, qry_property1 text, qry_property2 text, qry_property3 text, "
                        + "qry_property4 text, qry_property5 text, qry_property6 text, qry_property7 text, "
                        + "qry_property8 text, qry_property9 text, qry_property10 text, created_at timestamptz, "
                        + "modified_at timestamptz, record_hash text, source_system text, source_interface text, "
                        + "source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.appendSql =
                "INSERT INTO " + targetTable + " ("
                        + "hist_event_id, source_id, name, entity_id, entity_name, defined_entity_name, event_type, "
                        + "event_category, event_source_type, event_date, event_detail_json, prev_capture_id, "
                        + "capture_id, audit_event_id, property_name, old_value, new_value, account_id, acct_app_id, "
                        + "acct_app_name, acct_app_instance, acct_display_name, acct_native_id, pending_request_item_id, "
                        + "request_item_id, identity_request_id, identity_entitlement_id, qry_property1, qry_property2, "
                        + "qry_property3, qry_property4, qry_property5, qry_property6, qry_property7, qry_property8, "
                        + "qry_property9, qry_property10, created_at, modified_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (hist_event_id) DO NOTHING RETURNING hist_event_id";
    }

    @Override public String entity() { return "HistoricalRoleEvent"; }
    @Override public String targetTable() { return targetTable; }

    @Override
    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    @Override
    public boolean append(Connection conn, JsonNode r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql)) {
            int i = 1;
            ps.setString(i++, canonicalId(NativeJsonRead.text(r, "sourceId")));
            ps.setString(i++, NativeJsonRead.text(r, "sourceId"));
            ps.setString(i++, NativeJsonRead.text(r, "name"));
            ps.setString(i++, NativeJsonRead.text(r, "entityId"));
            ps.setString(i++, NativeJsonRead.text(r, "entityName"));
            ps.setString(i++, NativeJsonRead.text(r, "definedEntityName"));
            ps.setString(i++, NativeJsonRead.text(r, "eventType"));
            ps.setString(i++, NativeJsonRead.text(r, "eventCategory"));
            ps.setString(i++, NativeJsonRead.text(r, "eventSourceType"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "eventDate"));
            ps.setString(i++, NativeJsonRead.text(r, "eventDetailJson"));
            ps.setString(i++, NativeJsonRead.text(r, "prevCaptureId"));
            ps.setString(i++, NativeJsonRead.text(r, "captureId"));
            ps.setString(i++, NativeJsonRead.text(r, "auditEventId"));
            ps.setString(i++, NativeJsonRead.text(r, "propertyName"));
            ps.setString(i++, NativeJsonRead.text(r, "oldValue"));
            ps.setString(i++, NativeJsonRead.text(r, "newValue"));
            ps.setString(i++, NativeJsonRead.text(r, "accountId"));
            ps.setString(i++, NativeJsonRead.text(r, "acctAppId"));
            ps.setString(i++, NativeJsonRead.text(r, "acctAppName"));
            ps.setString(i++, NativeJsonRead.text(r, "acctAppInstance"));
            ps.setString(i++, NativeJsonRead.text(r, "acctDisplayName"));
            ps.setString(i++, NativeJsonRead.text(r, "acctNativeId"));
            ps.setString(i++, NativeJsonRead.text(r, "pendingRequestItemId"));
            ps.setString(i++, NativeJsonRead.text(r, "requestItemId"));
            ps.setString(i++, NativeJsonRead.text(r, "identityRequestId"));
            ps.setString(i++, NativeJsonRead.text(r, "identityEntitlementId"));
            for (int q = 1; q <= 10; q++) {
                ps.setString(i++, NativeJsonRead.text(r, "qryProperty" + q));
            }
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "created"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "modified"));
            ps.setString(i++, recordHash(r));
            ps.setString(i++, "IdentityIQ");
            ps.setString(i++, "native_iiq_java_api");
            ps.setString(i++, SRC_OBJECT_TYPE);
            ps.setString(i, extractionRunId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static String canonicalId(String sourceId) {
        String id = ParquetIds.canonicalUuid(sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-hist-role-event|" + sourceId);
        }
        return id;
    }

    static String recordHash(JsonNode r) {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        for (String f : new String[]{"sourceId", "entityId", "eventType", "eventCategory", "eventSourceType",
                "eventDate", "captureId", "prevCaptureId", "propertyName", "oldValue", "newValue", "accountId",
                "acctAppId", "requestItemId", "identityRequestId", "identityEntitlementId", "eventDetailJson"}) {
            b.put(f, NativeJsonRead.text(r, f));
        }
        return NativeRecordHash.of(b);
    }
}
