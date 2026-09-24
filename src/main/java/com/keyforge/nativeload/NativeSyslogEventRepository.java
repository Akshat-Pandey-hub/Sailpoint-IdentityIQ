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
 * Plain-JDBC, APPEND-ONLY persistence for native SyslogEvent rows into {@code <schema>.kf_syslog_event}.
 * SyslogEvents are immutable operational log records: writes are strictly {@code INSERT … ON CONFLICT
 * (syslogid) DO NOTHING} — no UPDATE, no DELETE, no sweep. PK {@code syslogid} is the deterministic
 * canonical UUID of the source id, so re-runs never duplicate.
 */
public final class NativeSyslogEventRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    public enum AppendOutcome { INSERTED, SKIPPED }

    public NativeSyslogEventRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_syslog_event";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "syslogid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "quick_key text, "
                        + "event_level text, "
                        + "server text, "
                        + "username text, "
                        + "thread text, "
                        + "line_number text, "
                        + "message text, "
                        + "stacktrace text, "
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
                        + "syslogid, source_id, quick_key, event_level, server, username, thread, line_number, "
                        + "message, stacktrace, created_at, record_hash, source_system, source_interface, "
                        + "source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (syslogid) DO NOTHING RETURNING syslogid";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public static String canonicalSyslogId(NativeSyslogEventRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-syslog|" + (r.sourceId == null ? r.quickKey : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativeSyslogEventRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("quick_key", r.quickKey);
        b.put("event_level", r.eventLevel);
        b.put("server", r.server);
        b.put("username", r.username);
        b.put("thread", r.thread);
        b.put("line_number", r.lineNumber);
        b.put("message", r.message);
        b.put("stacktrace", r.stacktrace);
        b.put("created_at", r.created);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    /** Appends one syslog event. INSERTED if a new row was written; SKIPPED if the id already existed. */
    public AppendOutcome append(Connection conn, NativeSyslogEventRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql)) {
            int i = 1;
            ps.setString(i++, canonicalSyslogId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.quickKey);
            ps.setString(i++, r.eventLevel);
            ps.setString(i++, r.server);
            ps.setString(i++, r.username);
            ps.setString(i++, r.thread);
            ps.setString(i++, r.lineNumber);
            ps.setString(i++, r.message);
            ps.setString(i++, r.stacktrace);
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
