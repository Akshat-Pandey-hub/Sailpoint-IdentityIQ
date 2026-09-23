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
 * Plain-JDBC persistence for native Certification rows into {@code <schema>.kf_certification} (current-state:
 * idempotent upsert on the deterministic canonical UUID of the Certification id; soft-delete via the shared
 * sweeper). Business-content {@code record_hash} for change detection.
 */
public final class NativeCertificationRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeCertificationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_certification";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "certificationid uuid PRIMARY KEY, source_id text, name text, certification_name text, "
                        + "short_name text, type text, phase text, comments text, creator text, manager text, "
                        + "certification_group_id text, certification_group_name text, certification_definition_id text, "
                        + "group_definition_id text, group_definition_name text, application_id text, "
                        + "task_schedule_id text, trigger_id text, parent_id text, complete boolean, expired boolean, "
                        + "continuous boolean, electronically_signed boolean, signed timestamptz, finished timestamptz, "
                        + "activated timestamptz, expiration timestamptz, created_at timestamptz, modified_at timestamptz, "
                        + "total_items integer, completed_items integer, open_items integer, total_entities integer, "
                        + "completed_entities integer, open_entities integer, percent_complete integer, "
                        + "certifiers jsonb, sign_off_history jsonb, owner_id text, owner_name text, record_hash text, "
                        + "source_system text, source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "certificationid, source_id, name, certification_name, short_name, type, phase, comments, "
                        + "creator, manager, certification_group_id, certification_group_name, "
                        + "certification_definition_id, group_definition_id, group_definition_name, application_id, "
                        + "task_schedule_id, trigger_id, parent_id, complete, expired, continuous, "
                        + "electronically_signed, signed, finished, activated, expiration, created_at, modified_at, "
                        + "total_items, completed_items, open_items, total_entities, completed_entities, open_entities, "
                        + "percent_complete, certifiers, sign_off_history, owner_id, owner_name, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (certificationid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, "
                        + "certification_name = EXCLUDED.certification_name, short_name = EXCLUDED.short_name, "
                        + "type = EXCLUDED.type, phase = EXCLUDED.phase, comments = EXCLUDED.comments, "
                        + "creator = EXCLUDED.creator, manager = EXCLUDED.manager, "
                        + "certification_group_id = EXCLUDED.certification_group_id, "
                        + "certification_group_name = EXCLUDED.certification_group_name, "
                        + "certification_definition_id = EXCLUDED.certification_definition_id, "
                        + "group_definition_id = EXCLUDED.group_definition_id, "
                        + "group_definition_name = EXCLUDED.group_definition_name, application_id = EXCLUDED.application_id, "
                        + "task_schedule_id = EXCLUDED.task_schedule_id, trigger_id = EXCLUDED.trigger_id, "
                        + "parent_id = EXCLUDED.parent_id, complete = EXCLUDED.complete, expired = EXCLUDED.expired, "
                        + "continuous = EXCLUDED.continuous, electronically_signed = EXCLUDED.electronically_signed, "
                        + "signed = EXCLUDED.signed, finished = EXCLUDED.finished, activated = EXCLUDED.activated, "
                        + "expiration = EXCLUDED.expiration, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, total_items = EXCLUDED.total_items, "
                        + "completed_items = EXCLUDED.completed_items, open_items = EXCLUDED.open_items, "
                        + "total_entities = EXCLUDED.total_entities, completed_entities = EXCLUDED.completed_entities, "
                        + "open_entities = EXCLUDED.open_entities, percent_complete = EXCLUDED.percent_complete, "
                        + "certifiers = EXCLUDED.certifiers, sign_off_history = EXCLUDED.sign_off_history, "
                        + "owner_id = EXCLUDED.owner_id, owner_name = EXCLUDED.owner_name, "
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

    public static String canonicalCertificationId(NativeCertificationRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-certification|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    public static String recordHash(NativeCertificationRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("phase", r.phase);
        b.put("comments", r.comments);
        b.put("creator", r.creator);
        b.put("manager", r.manager);
        b.put("certification_group_id", r.certificationGroupId);
        b.put("certification_definition_id", r.certificationDefinitionId);
        b.put("complete", r.complete);
        b.put("expired", r.expired);
        b.put("signed", r.signed);
        b.put("finished", r.finished);
        b.put("activated", r.activated);
        b.put("expiration", r.expiration);
        b.put("total_items", r.totalItems);
        b.put("completed_items", r.completedItems);
        b.put("open_items", r.openItems);
        b.put("percent_complete", r.percentComplete);
        b.put("certifiers", r.certifiersJson);
        b.put("sign_off_history", r.signOffHistoryJson);
        b.put("owner_id", r.ownerId);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeCertificationRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalCertificationId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.certificationName);
            ps.setString(i++, r.shortName);
            ps.setString(i++, r.type);
            ps.setString(i++, r.phase);
            ps.setString(i++, r.comments);
            ps.setString(i++, r.creator);
            ps.setString(i++, r.manager);
            ps.setString(i++, r.certificationGroupId);
            ps.setString(i++, r.certificationGroupName);
            ps.setString(i++, r.certificationDefinitionId);
            ps.setString(i++, r.groupDefinitionId);
            ps.setString(i++, r.groupDefinitionName);
            ps.setString(i++, r.applicationId);
            ps.setString(i++, r.taskScheduleId);
            ps.setString(i++, r.triggerId);
            ps.setString(i++, r.parentId);
            setBool(ps, i++, r.complete);
            setBool(ps, i++, r.expired);
            setBool(ps, i++, r.continuous);
            setBool(ps, i++, r.electronicallySigned);
            setTs(ps, i++, r.signed);
            setTs(ps, i++, r.finished);
            setTs(ps, i++, r.activated);
            setTs(ps, i++, r.expiration);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            setInt(ps, i++, r.totalItems);
            setInt(ps, i++, r.completedItems);
            setInt(ps, i++, r.openItems);
            setInt(ps, i++, r.totalEntities);
            setInt(ps, i++, r.completedEntities);
            setInt(ps, i++, r.openEntities);
            setInt(ps, i++, r.percentComplete);
            ps.setString(i++, r.certifiersJson);
            ps.setString(i++, r.signOffHistoryJson);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
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
