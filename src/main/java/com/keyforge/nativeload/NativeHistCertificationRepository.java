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
 * APPEND-ONLY persistence for native {@code HistoricalCertification} rows into
 * {@code <schema>.kf_access_hist_certification}. Immutable historical evidence: {@code INSERT … ON CONFLICT
 * (hist_cert_id) DO NOTHING}. {@code cert_json} is the raw source payload (text). PK = canonical UUID of the
 * source id.
 */
public final class NativeHistCertificationRepository implements NativeAccessHistoryRepo {

    static final String SRC_OBJECT_TYPE = "sailpoint.object.accesshistory.HistoricalCertification";

    private final String extractionRunId;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    public NativeHistCertificationRepository(String schema, String extractionRunId) {
        String s = SchemaName.validate(schema);
        this.extractionRunId = extractionRunId;
        this.targetTable = s + ".kf_access_hist_certification";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + s;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "hist_cert_id uuid PRIMARY KEY, source_id text, name text, cert_id text, cert_type text, "
                        + "cert_name text, cert_display_name text, finished timestamptz, signed timestamptz, "
                        + "cert_json text, created_at timestamptz, modified_at timestamptz, record_hash text, "
                        + "source_system text, source_interface text, source_object_type text, "
                        + "extraction_run_id text, extracted_at timestamptz NOT NULL DEFAULT now())";
        this.appendSql =
                "INSERT INTO " + targetTable + " ("
                        + "hist_cert_id, source_id, name, cert_id, cert_type, cert_name, cert_display_name, "
                        + "finished, signed, cert_json, created_at, modified_at, record_hash, source_system, "
                        + "source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (hist_cert_id) DO NOTHING RETURNING hist_cert_id";
    }

    @Override public String entity() { return "HistoricalCertification"; }
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
            ps.setString(i++, NativeJsonRead.text(r, "certId"));
            ps.setString(i++, NativeJsonRead.text(r, "certType"));
            ps.setString(i++, NativeJsonRead.text(r, "certName"));
            ps.setString(i++, NativeJsonRead.text(r, "certDisplayName"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "finished"));
            NativeJsonRead.setTs(ps, i++, NativeJsonRead.instant(r, "signed"));
            ps.setString(i++, NativeJsonRead.text(r, "certJson"));
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
            id = ParquetIds.deterministicUuid("native-hist-cert|" + sourceId);
        }
        return id;
    }

    static String recordHash(JsonNode r) {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        for (String f : new String[]{"sourceId", "certId", "certType", "certName", "certDisplayName",
                "finished", "signed", "certJson"}) {
            b.put(f, NativeJsonRead.text(r, f));
        }
        return NativeRecordHash.of(b);
    }
}
