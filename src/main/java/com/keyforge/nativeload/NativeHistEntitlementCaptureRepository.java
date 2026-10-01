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
 * APPEND-ONLY persistence for native {@code HistoricalEntitlementCapture} rows into
 * {@code <schema>.kf_access_hist_entitlement}. Immutable historical evidence: {@code INSERT … ON CONFLICT
 * (hist_capture_id) DO NOTHING}. PK is the deterministic canonical UUID of the source id, so re-runs never
 * duplicate. Source JSON payloads are kept verbatim (text); built attribute maps + the compressible-property
 * list are stored as jsonb. Column {@code full_capture} carries the {@code full} flag ({@code FULL} is reserved).
 */
public final class NativeHistEntitlementCaptureRepository implements NativeAccessHistoryRepo {

    static final String SRC_OBJECT_TYPE = "sailpoint.object.accesshistory.HistoricalEntitlementCapture";

    private final String extractionRunId;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    public NativeHistEntitlementCaptureRepository(String schema, String extractionRunId) {
        String s = SchemaName.validate(schema);
        this.extractionRunId = extractionRunId;
        this.targetTable = s + ".kf_access_hist_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + s;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "hist_capture_id uuid PRIMARY KEY, source_id text, name text, "
                        + "entity_id text, entity_name text, identity_id text, identity_name text, "
                        + "identity_entitlement_id text, application_id text, application_name text, "
                        + "native_identity text, instance text, display_value text, attribute_name text, "
                        + "attribute_value text, type text, granted_by_role boolean, role_id text, "
                        + "request_item_id text, pending_request_item_id text, certification_item_id text, "
                        + "deleted boolean, effective_date timestamptz, extended_to_date timestamptz, "
                        + "latest boolean, compressed boolean, brief boolean, full_capture boolean, patch boolean, "
                        + "smart_hash text, full_hash text, json_format text, transform_type text, "
                        + "patch_doc_parent text, compressed_property_flag text, compressed_property_flag_value text, "
                        + "compressible_property_names jsonb, capture_json text, attributes jsonb, "
                        + "extended_attributes jsonb, created_at timestamptz, modified_at timestamptz, "
                        + "record_hash text, source_system text, source_interface text, source_object_type text, "
                        + "extraction_run_id text, extracted_at timestamptz NOT NULL DEFAULT now())";
        this.appendSql =
                "INSERT INTO " + targetTable + " ("
                        + "hist_capture_id, source_id, name, entity_id, entity_name, identity_id, identity_name, "
                        + "identity_entitlement_id, application_id, application_name, native_identity, instance, "
                        + "display_value, attribute_name, attribute_value, type, granted_by_role, role_id, "
                        + "request_item_id, pending_request_item_id, certification_item_id, deleted, effective_date, "
                        + "extended_to_date, latest, compressed, brief, full_capture, patch, smart_hash, full_hash, "
                        + "json_format, transform_type, patch_doc_parent, compressed_property_flag, "
                        + "compressed_property_flag_value, compressible_property_names, capture_json, attributes, "
                        + "extended_attributes, created_at, modified_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?, ?::jsonb, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (hist_capture_id) DO NOTHING RETURNING hist_capture_id";
    }

    @Override public String entity() { return "HistoricalEntitlementCapture"; }
    @Override public String targetTable() { return targetTable; }

    /**
     * Additive, non-destructive migrations for a table created by an earlier version of this repository:
     * {@code CREATE TABLE IF NOT EXISTS} never alters an existing table, so a pre-existing
     * {@code kf_access_hist_entitlement} can be missing the columns added since. Each is
     * {@code ADD COLUMN IF NOT EXISTS} — a no-op on a current table, harmless on an older one, never drops or
     * rewrites data. Keeps the live table's column set in lock-step with the INSERT above.
     */
    private static final String[] ADD_COLUMNS = {
            "name text",
            "compressed_property_flag text",
            "compressed_property_flag_value text",
            "compressible_property_names jsonb",
    };

    @Override
    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
            for (String col : ADD_COLUMNS) {
                st.execute("ALTER TABLE " + targetTable + " ADD COLUMN IF NOT EXISTS " + col);
            }
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
            ps.setString(i++, NativeJsonRead.text(r, "identityId"));
            ps.setString(i++, NativeJsonRead.text(r, "identityName"));
            ps.setString(i++, NativeJsonRead.text(r, "identityEntitlementId"));
            ps.setString(i++, NativeJsonRead.text(r, "applicationId"));
            ps.setString(i++, NativeJsonRead.text(r, "applicationName"));
            ps.setString(i++, NativeJsonRead.text(r, "nativeIdentity"));
            ps.setString(i++, NativeJsonRead.text(r, "instance"));
            ps.setString(i++, NativeJsonRead.text(r, "displayValue"));
            ps.setString(i++, NativeJsonRead.text(r, "attributeName"));
            ps.setString(i++, NativeJsonRead.text(r, "attributeValue"));
            ps.setString(i++, NativeJsonRead.text(r, "type"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "grantedByRole"));
            ps.setString(i++, NativeJsonRead.text(r, "roleId"));
            ps.setString(i++, NativeJsonRead.text(r, "requestItemId"));
            ps.setString(i++, NativeJsonRead.text(r, "pendingRequestItemId"));
            ps.setString(i++, NativeJsonRead.text(r, "certificationItemId"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "deleted"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "effectiveDate"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "extendedToDate"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "latest"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "compressed"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "brief"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "full"));
            NativeJsonRead.setBool(ps, i++, NativeJsonRead.bool(r, "patch"));
            ps.setString(i++, NativeJsonRead.text(r, "smartHash"));
            ps.setString(i++, NativeJsonRead.text(r, "fullHash"));
            ps.setString(i++, NativeJsonRead.text(r, "jsonFormat"));
            ps.setString(i++, NativeJsonRead.text(r, "transformType"));
            ps.setString(i++, NativeJsonRead.text(r, "patchDocParent"));
            ps.setString(i++, NativeJsonRead.text(r, "compressedPropertyFlag"));
            ps.setString(i++, NativeJsonRead.text(r, "compressedPropertyFlagValue"));
            ps.setString(i++, NativeJsonRead.jsonString(r, "compressiblePropertyNames"));
            ps.setString(i++, NativeJsonRead.text(r, "captureJson"));
            ps.setString(i++, NativeJsonRead.jsonString(r, "attributes"));
            ps.setString(i++, NativeJsonRead.jsonString(r, "extendedAttributes"));
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
            id = ParquetIds.deterministicUuid("native-hist-ent|" + sourceId);
        }
        return id;
    }

    static String recordHash(JsonNode r) {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        for (String f : new String[]{"sourceId", "entityId", "identityId", "identityEntitlementId",
                "applicationId", "attributeName", "attributeValue", "type", "grantedByRole", "roleId",
                "requestItemId", "certificationItemId", "deleted", "effectiveDate", "latest", "smartHash",
                "fullHash", "captureJson", "attributes", "extendedAttributes"}) {
            b.put(f, NativeJsonRead.text(r, f));
        }
        return NativeRecordHash.of(b);
    }
}
