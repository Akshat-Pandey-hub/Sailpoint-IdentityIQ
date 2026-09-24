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
 * Plain-JDBC, APPEND-ONLY persistence for native AuditEvent rows into {@code <schema>.kf_audit_event}.
 * AuditEvents are immutable historical evidence: writes are strictly {@code INSERT … ON CONFLICT
 * (auditid) DO NOTHING} — no UPDATE, no DELETE, no sweep. PK {@code auditid} is the deterministic
 * canonical UUID of the source id, so re-runs never duplicate.
 */
public final class NativeAuditEventRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    public enum AppendOutcome { INSERTED, SKIPPED }

    public NativeAuditEventRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_audit_event";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "auditid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "action text, "
                        + "audit_source text, "
                        + "target text, "
                        + "application text, "
                        + "account_name text, "
                        + "instance text, "
                        + "attribute_name text, "
                        + "attribute_value text, "
                        + "interface_name text, "
                        + "server_host text, "
                        + "client_host text, "
                        + "tracking_id text, "
                        + "string1 text, "
                        + "string2 text, "
                        + "string3 text, "
                        + "string4 text, "
                        + "attributes jsonb, "
                        + "created_at timestamptz, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.appendSql =
                "INSERT INTO " + targetTable + " ("
                        + "auditid, source_id, action, audit_source, target, application, account_name, instance, "
                        + "attribute_name, attribute_value, interface_name, server_host, client_host, tracking_id, "
                        + "string1, string2, string3, string4, attributes, created_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (auditid) DO NOTHING RETURNING auditid";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public static String canonicalAuditId(NativeAuditEventRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-audit|" + (r.sourceId == null ? r.action : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativeAuditEventRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("action", r.action);
        b.put("audit_source", r.auditSource);
        b.put("target", r.target);
        b.put("application", r.application);
        b.put("account_name", r.accountName);
        b.put("instance", r.instance);
        b.put("attribute_name", r.attributeName);
        b.put("attribute_value", r.attributeValue);
        b.put("interface_name", r.interfaceName);
        b.put("server_host", r.serverHost);
        b.put("client_host", r.clientHost);
        b.put("tracking_id", r.trackingId);
        b.put("string1", r.string1);
        b.put("string2", r.string2);
        b.put("string3", r.string3);
        b.put("string4", r.string4);
        b.put("attributes", r.attributesJson);
        b.put("created_at", r.created);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    /** Appends one audit event. INSERTED if a new row was written; SKIPPED if the id already existed. */
    public AppendOutcome append(Connection conn, NativeAuditEventRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql)) {
            int i = 1;
            ps.setString(i++, canonicalAuditId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.action);
            ps.setString(i++, r.auditSource);
            ps.setString(i++, r.target);
            ps.setString(i++, r.application);
            ps.setString(i++, r.accountName);
            ps.setString(i++, r.instance);
            ps.setString(i++, r.attributeName);
            ps.setString(i++, r.attributeValue);
            ps.setString(i++, r.interfaceName);
            ps.setString(i++, r.serverHost);
            ps.setString(i++, r.clientHost);
            ps.setString(i++, r.trackingId);
            ps.setString(i++, r.string1);
            ps.setString(i++, r.string2);
            ps.setString(i++, r.string3);
            ps.setString(i++, r.string4);
            ps.setString(i++, r.attributesJson);
            setTs(ps, i++, r.created);
            ps.setString(i++, recordHash(r));
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.srcObjectType);
            ps.setString(i, r.extractionRunId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? AppendOutcome.INSERTED : AppendOutcome.SKIPPED;
            }
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
