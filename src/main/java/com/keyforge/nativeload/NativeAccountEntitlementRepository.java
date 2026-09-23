package com.keyforge.nativeload;

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
 * Plain-JDBC persistence for native account-entitlement edges into {@code <schema>.kf_account_entitlement}
 * — the current-state account&nbsp;&harr;&nbsp;entitlement relationship (values aggregated on the account).
 *
 * <p>Idempotent current-state: PK {@code accountentitlementid} is the deterministic UUID of
 * {@code (link id | attribute name | value)} (these edges are not standalone persisted objects, so there is
 * no source id to canonicalize). Every write is {@code INSERT … ON CONFLICT DO UPDATE}; deletions use the
 * shared {@link com.keyforge.iiq.deletion.SoftDeleteSweeper}.
 */
public final class NativeAccountEntitlementRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeAccountEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_account_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "accountentitlementid uuid PRIMARY KEY, "
                        + "link_id text, "
                        + "identity_id text, "
                        + "identity_name text, "
                        + "application_id text, "
                        + "application_name text, "
                        + "native_identity text, "
                        + "instance text, "
                        + "attribute_name text, "
                        + "attribute_value text, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "accountentitlementid, link_id, identity_id, identity_name, application_id, "
                        + "application_name, native_identity, instance, attribute_name, attribute_value, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (accountentitlementid) DO UPDATE SET "
                        + "link_id = EXCLUDED.link_id, identity_id = EXCLUDED.identity_id, "
                        + "identity_name = EXCLUDED.identity_name, application_id = EXCLUDED.application_id, "
                        + "application_name = EXCLUDED.application_name, native_identity = EXCLUDED.native_identity, "
                        + "instance = EXCLUDED.instance, attribute_name = EXCLUDED.attribute_name, "
                        + "attribute_value = EXCLUDED.attribute_value, record_hash = EXCLUDED.record_hash, "
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

    /** Deterministic PK from (link id | attribute name | value). Never random. */
    public static String canonicalEdgeId(NativeAccountEntitlementRecord r) {
        return ParquetIds.deterministicUuid("native-account-entitlement|"
                + r.linkId + "|" + r.attributeName + "|" + r.attributeValue);
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeAccountEntitlementRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("link_id", r.linkId);
        b.put("identity_id", r.identityId);
        b.put("application_name", r.applicationName);
        b.put("native_identity", r.nativeIdentity);
        b.put("instance", r.instance);
        b.put("attribute_name", r.attributeName);
        b.put("attribute_value", r.attributeValue);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeAccountEntitlementRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalEdgeId(r));
            ps.setString(i++, r.linkId);
            ps.setString(i++, r.identityId);
            ps.setString(i++, r.identityName);
            ps.setString(i++, r.applicationId);
            ps.setString(i++, r.applicationName);
            ps.setString(i++, r.nativeIdentity);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.attributeName);
            ps.setString(i++, r.attributeValue);
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
}
