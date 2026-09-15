package com.keyforge.iiq.identityrole;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the first-class {@code kf_identity_role} relationship. Explicit DDL inside
 * {@code PG_SCHEMA}; idempotent upsert on {@code id}. {@code identityid} references
 * {@code kf_identity.userid} and {@code roleid} references {@code kf_role.roleid} (same canonical
 * UUIDs); a UNIQUE (identityid, roleid) guards the natural key.
 */
public class IdentityRoleRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;

    public IdentityRoleRepository() {
        this(DEFAULT_SCHEMA);
    }

    public IdentityRoleRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_role";
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
                    + "id uuid PRIMARY KEY, "
                    + "identityid uuid, "
                    + "roleid uuid, "
                    + "source_identity_id text, "
                    + "source_role_id text, "
                    + "role_display_name text, "
                    + "assigned_at timestamptz, "
                    + "assigner text, "
                    + "description text, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now(), "
                    + "CONSTRAINT kf_identity_role_uq UNIQUE (identityid, roleid))");
        }
    }

    public UpsertOutcome upsert(Connection conn, IdentityRoleRow r) throws SQLException {
        String sql = "INSERT INTO " + targetTable + " (id, identityid, roleid, source_identity_id, source_role_id, "
                + "role_display_name, assigned_at, assigner, description) "
                + "VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?,?) "
                + "ON CONFLICT (id) DO UPDATE SET identityid=EXCLUDED.identityid, roleid=EXCLUDED.roleid, "
                + "source_identity_id=EXCLUDED.source_identity_id, source_role_id=EXCLUDED.source_role_id, "
                + "role_display_name=EXCLUDED.role_display_name, assigned_at=EXCLUDED.assigned_at, "
                + "assigner=EXCLUDED.assigner, description=EXCLUDED.description, extracted_at=now() "
                + "RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.id());
            ps.setString(i++, r.identityid());
            ps.setString(i++, r.roleid());
            ps.setString(i++, r.sourceIdentityId());
            ps.setString(i++, r.sourceRoleId());
            ps.setString(i++, r.roleDisplayName());
            setTs(ps, i++, r.assignedAt());
            ps.setString(i++, r.assigner());
            ps.setString(i++, r.description());
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
