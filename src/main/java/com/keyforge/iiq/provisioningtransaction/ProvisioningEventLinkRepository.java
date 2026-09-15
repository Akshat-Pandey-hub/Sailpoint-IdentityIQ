package com.keyforge.iiq.provisioningtransaction;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain-JDBC data access for the provisioning-transaction side of {@code kf_event_link}. Writes into
 * the SAME {@code kf_event_link} table the audit side owns (per the PDF's single link entity), using
 * the generic {@code source_object_id} column for the ProvisioningTransaction id.
 *
 * <p><b>Smallest additive migration</b> (idempotent, non-destructive): the audit side created
 * {@code kf_event_link} with {@code audit_event_id uuid NOT NULL} and no generic source column, so
 * {@code ensureTargetTable} adds {@code source_object_id uuid} and drops the NOT NULL on
 * {@code audit_event_id} (which the audit side always populates anyway). Existing audit rows and the
 * audit-side code are untouched; the two sides are distinguished by {@code source_object_type}.
 */
public class ProvisioningEventLinkRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String table;

    public ProvisioningEventLinkRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ProvisioningEventLinkRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_event_link";
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    public String accessRequestTable() {
        return schema + ".kf_access_request";
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            // Fresh install: create in the generalised shape (audit_event_id nullable, generic source id).
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id uuid PRIMARY KEY, "
                    + "audit_event_id uuid, "
                    + "source_object_id uuid, "
                    + "source_object_type text, "
                    + "target_object_type text, "
                    + "target_object_id uuid, "
                    + "target_raw text NOT NULL, "
                    + "target_type_hint text, "
                    + "link_status text NOT NULL, "
                    + "resolution_rule text, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
            // Existing install (audit side already created it): additive, idempotent migration.
            st.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS source_object_id uuid");
            st.execute("ALTER TABLE " + table + " ALTER COLUMN audit_event_id DROP NOT NULL");
        }
    }

    public UpsertOutcome upsert(Connection conn, ProvisioningEventLinkRow r) throws SQLException {
        String sql = "INSERT INTO " + table + " (id, source_object_id, source_object_type, target_object_type, "
                + "target_object_id, target_raw, target_type_hint, link_status, resolution_rule) "
                + "VALUES (?::uuid,?::uuid,?,?,?::uuid,?,?,?,?) "
                + "ON CONFLICT (id) DO UPDATE SET source_object_id=EXCLUDED.source_object_id, "
                + "source_object_type=EXCLUDED.source_object_type, target_object_type=EXCLUDED.target_object_type, "
                + "target_object_id=EXCLUDED.target_object_id, target_raw=EXCLUDED.target_raw, "
                + "target_type_hint=EXCLUDED.target_type_hint, link_status=EXCLUDED.link_status, "
                + "resolution_rule=EXCLUDED.resolution_rule, extracted_at=now() "
                + "RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.id());
            ps.setString(2, r.sourceObjectId());
            ps.setString(3, r.sourceObjectType());
            ps.setString(4, r.targetObjectType());
            ps.setString(5, r.targetObjectId());
            ps.setString(6, r.targetRaw());
            ps.setString(7, r.targetTypeHint());
            ps.setString(8, r.linkStatus());
            ps.setString(9, r.resolutionRule());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    /**
     * Builds a resolution map for {@code accessRequestId} → {@code requestid}, keyed by both the
     * {@code request_number} and the canonical {@code requestid} of {@code kf_access_request}. A key
     * that maps to more than one distinct requestid is retained so the resolver can flag it ambiguous
     * rather than guess. Empty if the table is absent.
     */
    public Map<String, Set<String>> loadRequestRefMap(Connection conn) throws SQLException {
        Map<String, Set<String>> map = new HashMap<>();
        if (!tableExists(conn, accessRequestTable())) {
            return map;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT requestid::text, request_number FROM " + accessRequestTable())) {
            while (rs.next()) {
                String requestid = rs.getString(1);
                if (requestid == null) {
                    continue;
                }
                addKey(map, requestid, requestid);
                addKey(map, rs.getString(2), requestid);
            }
        }
        return map;
    }

    private static void addKey(Map<String, Set<String>> map, String key, String requestid) {
        if (key == null || key.isBlank()) {
            return;
        }
        map.computeIfAbsent(key, k -> new HashSet<>()).add(requestid);
    }

    private boolean tableExists(Connection conn, String qualifiedTable) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?)")) {
            ps.setString(1, qualifiedTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }
}
