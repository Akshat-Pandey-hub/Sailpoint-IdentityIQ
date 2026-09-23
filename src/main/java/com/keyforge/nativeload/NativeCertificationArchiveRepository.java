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
 * Plain-JDBC persistence for native CertificationArchive rows into
 * {@code <schema>.kf_certification_archive}.
 *
 * <p><b>CEC / append-only semantics.</b> A CertificationArchive is an immutable historical record: it is
 * written once when a certification is archived and is never mutated. This repository therefore:
 * <ul>
 *   <li>keys on {@code certificationarchiveid} = the deterministic canonical UUID of the archive's own
 *       {@code getId()};</li>
 *   <li>writes strictly {@code INSERT ... ON CONFLICT (certificationarchiveid) DO NOTHING} — no
 *       {@code UPDATE}, no {@code DELETE}, and there is no soft-delete sweep (nothing to expire);</li>
 *   <li>carries the CEC lineage envelope with {@code src_event_ts} = the archival timestamp
 *       ({@code getCreated()}), so re-running the extraction never duplicates and never rewrites a row.</li>
 * </ul>
 * Each row carries a deterministic {@code record_hash} over its business content (excludes lineage +
 * {@code extracted_at}) purely for change-auditing; because archives are immutable it does not drive any
 * update path.
 */
public final class NativeCertificationArchiveRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String appendSql;

    /** Outcome of an append: a new row was written, or an identical archive already existed (deduped). */
    public enum AppendOutcome { INSERTED, SKIPPED }

    public NativeCertificationArchiveRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_certification_archive";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "certificationarchiveid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "certification_id text, "
                        + "certification_group_id text, "
                        + "creator_name text, "
                        + "owner_name text, "
                        + "comments text, "
                        + "signed timestamptz, "
                        + "expiration timestamptz, "
                        + "child_certification_ids jsonb, "
                        + "archive_xml text, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
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
                        + "certificationarchiveid, source_id, name, certification_id, certification_group_id, "
                        + "creator_name, owner_name, comments, signed, expiration, child_certification_ids, "
                        + "archive_xml, created_at, modified_at, archived_ts, record_hash, src_system, "
                        + "src_object_type, src_object_id, src_natural_key, src_created, src_modified, "
                        + "src_event_ts, src_interface, extraction_run_id, raw_ref, derived_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (certificationarchiveid) DO NOTHING "
                        + "RETURNING certificationarchiveid";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    /** Deterministic PK from the archive object's own source id (fallback: deterministic name-based UUID). */
    public static String canonicalArchiveId(NativeCertificationArchiveRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            if (r.sourceId == null || r.sourceId.isBlank()) {
                throw new IllegalArgumentException(
                        "CertificationArchive source id is required; certificationarchiveid must derive from getId()");
            }
            id = ParquetIds.deterministicUuid("native-certification-archive|" + r.sourceId);
        }
        return id;
    }

    /** Deterministic SHA-256 over the business content (excludes lineage + extracted_at). */
    public static String recordHash(NativeCertificationArchiveRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("certification_id", r.certificationId);
        b.put("certification_group_id", r.certificationGroupId);
        b.put("creator_name", r.creatorName);
        b.put("owner_name", r.ownerName);
        b.put("comments", r.comments);
        b.put("signed", r.signed);
        b.put("child_certification_ids", r.childCertificationIdsJson);
        b.put("archive_xml", r.archiveXml);
        b.put("created_at", r.created);
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
     * {@link AppendOutcome#SKIPPED} if an archive with the same {@code certificationarchiveid} already
     * existed (deduplicated — not an error). Never updates or deletes.
     */
    public AppendOutcome append(Connection conn, NativeCertificationArchiveRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql)) {
            int i = 1;
            ps.setString(i++, canonicalArchiveId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.certificationGroupId);
            ps.setString(i++, r.creatorName);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.comments);
            setTs(ps, i++, r.signed);
            setTs(ps, i++, r.expiration);
            ps.setString(i++, r.childCertificationIdsJson);
            ps.setString(i++, r.archiveXml);
            setTs(ps, i++, r.created);               // created_at
            setTs(ps, i++, r.modified);              // modified_at
            setTs(ps, i++, r.created);               // archived_ts = created_at
            ps.setString(i++, recordHash(r));
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcObjectType);
            ps.setString(i++, r.sourceId);          // src_object_id = archive getId()
            ps.setString(i++, r.srcNaturalKey);
            setTs(ps, i++, r.created);               // src_created
            setTs(ps, i++, r.modified);              // src_modified
            setTs(ps, i++, r.srcEventTs);            // src_event_ts = created
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.extractionRunId);
            ps.setString(i++, null);                 // raw_ref — RAW zone optional, not wired into this path
            ps.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE); // derived_at — extracted source, not derived

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next(); // a returned id means a row was actually inserted
                return inserted ? AppendOutcome.INSERTED : AppendOutcome.SKIPPED;
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
