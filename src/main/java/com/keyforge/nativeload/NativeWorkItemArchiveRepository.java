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
 * Plain-JDBC persistence for native WorkItemArchive rows into {@code <schema>.kf_workitem_archive}.
 *
 * <p><b>CEC / append-only semantics.</b> A WorkItemArchive is an immutable historical record: it is written
 * once when the work item is archived and is never mutated. This repository therefore:
 * <ul>
 *   <li>keys on {@code archiveid} = the deterministic canonical UUID of the archive's own {@code getId()}
 *       (NOT {@code getWorkItemId()}, which points at the deleted live work item);</li>
 *   <li>writes strictly {@code INSERT ... ON CONFLICT (archiveid) DO NOTHING} — no {@code UPDATE},
 *       no {@code DELETE}, and there is no soft-delete sweep (nothing to expire);</li>
 *   <li>carries the CEC lineage envelope with {@code src_event_ts} = the archival timestamp
 *       ({@code getArchived()}), so re-running the extraction never duplicates and never rewrites a row.</li>
 * </ul>
 * Each row carries a deterministic {@code record_hash} over its business content (excludes lineage +
 * {@code extracted_at}) purely for change-auditing; because archives are immutable it does not drive any
 * update path.
 */
public final class NativeWorkItemArchiveRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    /** Outcome of an append: a new row was written, or an identical archive already existed (deduped). */
    public enum AppendOutcome { INSERTED, SKIPPED }

    public NativeWorkItemArchiveRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workitem_archive";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "archiveid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "work_item_id text, "
                        + "name text, "
                        + "type text, "
                        + "state text, "
                        + "level text, "
                        + "requester text, "
                        + "assignee text, "
                        + "owner_name text, "
                        + "completer text, "
                        + "completion_comments text, "
                        + "is_signed boolean, "
                        + "target_class text, "
                        + "target_id text, "
                        + "target_name text, "
                        + "identity_request_id text, "
                        + "certification_id text, "
                        + "certification_entity_id text, "
                        + "certification_item_id text, "
                        + "entity_type text, "
                        + "signoffs jsonb, "
                        + "comments jsonb, "
                        + "owner_history jsonb, "
                        + "system_attributes jsonb, "
                        + "attributes jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "expiration_ts timestamptz, "
                        + "archived_ts timestamptz, "
                        + "record_hash text, "
                        + "src_system text, "
                        + "src_object_type text, "
                        + "src_object_id text, "
                        + "src_natural_key text, "
                        + "src_created timestamptz, "
                        + "src_modified timestamptz, "
                        + "src_event_ts timestamptz, "
                        + "src_interface text, "
                        + "extraction_run_id text, "
                        + "raw_ref text, "
                        + "derived_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.appendSql = appendSql(targetTable);
    }

    /** Pure append-only insert builder, exposed package-private for regression tests. */
    static String appendSql(String table) {
        return "INSERT INTO " + table + " ("
                        + "archiveid, source_id, work_item_id, name, type, state, level, requester, assignee, "
                        + "owner_name, completer, completion_comments, is_signed, target_class, target_id, "
                        + "target_name, identity_request_id, certification_id, certification_entity_id, "
                        + "certification_item_id, entity_type, signoffs, comments, owner_history, "
                        + "system_attributes, attributes, created_at, modified_at, expiration_ts, archived_ts, "
                        + "record_hash, src_system, src_object_type, src_object_id, src_natural_key, "
                        + "src_created, src_modified, src_event_ts, src_interface, extraction_run_id, raw_ref, "
                        + "derived_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (archiveid) DO NOTHING "
                        + "RETURNING archiveid";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    /** Deterministic PK from the archive object's own source id. */
    public static String canonicalArchiveId(NativeWorkItemArchiveRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            throw new IllegalArgumentException("WorkItemArchive source id is required; archiveid must derive from getId()");
        }
        return id;
    }

    /** Deterministic SHA-256 over the business content (excludes lineage + extracted_at). */
    public static String recordHash(NativeWorkItemArchiveRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("work_item_id", r.workItemId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("state", r.state);
        b.put("level", r.level);
        b.put("requester", r.requester);
        b.put("assignee", r.assignee);
        b.put("owner_name", r.ownerName);
        b.put("completer", r.completer);
        b.put("completion_comments", r.completionComments);
        b.put("is_signed", r.signed);
        b.put("target_class", r.targetClass);
        b.put("target_id", r.targetId);
        b.put("target_name", r.targetName);
        b.put("identity_request_id", r.identityRequestId);
        b.put("certification_id", r.certificationId);
        b.put("certification_entity_id", r.certificationEntityId);
        b.put("certification_item_id", r.certificationItemId);
        b.put("entity_type", r.entityType);
        b.put("signoffs", r.signOffsJson);
        b.put("comments", r.commentsJson);
        b.put("owner_history", r.ownerHistoryJson);
        b.put("system_attributes", r.systemAttributesJson);
        b.put("attributes", r.attributesJson);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        b.put("expiration_ts", r.expiration);
        b.put("archived_ts", r.archived);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    /**
     * Appends one archive. Returns {@link AppendOutcome#INSERTED} if a new row was written, or
     * {@link AppendOutcome#SKIPPED} if an archive with the same {@code archiveid} already existed
     * (deduplicated — not an error). Never updates or deletes.
     */
    public AppendOutcome append(Connection conn, NativeWorkItemArchiveRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql)) {
            int i = 1;
            ps.setString(i++, canonicalArchiveId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.workItemId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.state);
            ps.setString(i++, r.level);
            ps.setString(i++, r.requester);
            ps.setString(i++, r.assignee);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.completer);
            ps.setString(i++, r.completionComments);
            setBool(ps, i++, r.signed);
            ps.setString(i++, r.targetClass);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.targetName);
            ps.setString(i++, r.identityRequestId);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.certificationEntityId);
            ps.setString(i++, r.certificationItemId);
            ps.setString(i++, r.entityType);
            ps.setString(i++, r.signOffsJson);
            ps.setString(i++, r.commentsJson);
            ps.setString(i++, r.ownerHistoryJson);
            ps.setString(i++, r.systemAttributesJson);
            ps.setString(i++, r.attributesJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            setTs(ps, i++, r.expiration);
            setTs(ps, i++, r.archived);
            ps.setString(i++, recordHash(r));
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcObjectType);
            ps.setString(i++, r.sourceId);          // src_object_id = archive getId()
            ps.setString(i++, r.srcNaturalKey);
            setTs(ps, i++, r.created);               // src_created
            setTs(ps, i++, r.modified);              // src_modified
            setTs(ps, i++, r.srcEventTs);            // src_event_ts = archived
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.extractionRunId);
            ps.setString(i++, null);                 // raw_ref — RAW zone optional, not wired into this path
            ps.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE); // derived_at — this is an extracted source, not derived

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next(); // a returned archiveid means a row was actually inserted
                return inserted ? AppendOutcome.INSERTED : AppendOutcome.SKIPPED;
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

    private static void setTs(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, value.atOffset(ZoneOffset.UTC));
        }
    }
}
