package com.keyforge.iiq.certification;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for {@code kf_certification_campaign}. Explicit DDL inside {@code PG_SCHEMA};
 * idempotent upsert on {@code campaignid}. {@code created_at} is {@code timestamptz} (bound as UTC).
 * {@code tags} is {@code jsonb} (raw source array preserved).
 *
 * <p>The columns {@code type}, {@code phase}, {@code start_date}, {@code end_date}, {@code signed_date}
 * exist to match the PDF campaign model but are <b>not populated</b>: the verified
 * {@code rest/certificationGroups} endpoint does not expose them, so they are left NULL rather than
 * fabricated. They are ready to fill if a richer certification interface (plugin/JDBC) becomes
 * available.
 */
public class CertificationCampaignRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;

    public CertificationCampaignRepository() {
        this(DEFAULT_SCHEMA);
    }

    public CertificationCampaignRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_certification_campaign";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                    + "campaignid uuid PRIMARY KEY, "
                    + "source_id text, "
                    + "name text, "
                    + "owner_display_name text, "
                    + "status text, "
                    + "percent_complete text, "
                    + "created_at timestamptz, "
                    + "tags jsonb, "
                    // PDF campaign fields not exposed by rest/certificationGroups — kept NULL, never fabricated:
                    + "type text, "
                    + "phase text, "
                    + "start_date timestamptz, "
                    + "end_date timestamptz, "
                    + "signed_date timestamptz, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, CertificationCampaignRow r) throws SQLException {
        String sql = "INSERT INTO " + targetTable + " (campaignid, source_id, name, owner_display_name, status, "
                + "percent_complete, created_at, tags) "
                + "VALUES (?::uuid,?,?,?,?,?,?,?::jsonb) "
                + "ON CONFLICT (campaignid) DO UPDATE SET source_id=EXCLUDED.source_id, name=EXCLUDED.name, "
                + "owner_display_name=EXCLUDED.owner_display_name, status=EXCLUDED.status, "
                + "percent_complete=EXCLUDED.percent_complete, created_at=EXCLUDED.created_at, tags=EXCLUDED.tags, "
                + "extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.campaignid());
            ps.setString(i++, r.sourceId());
            ps.setString(i++, r.name());
            ps.setString(i++, r.ownerDisplayName());
            ps.setString(i++, r.status());
            ps.setString(i++, r.percentComplete());
            setTs(ps, i++, r.createdAt());
            ps.setString(i++, r.tagsJson());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTs(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setObject(i, v.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
