package com.keyforge.iiq.eventlink;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-JDBC data access for the derived {@code kf_event_link} table (audit-event → object side).
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic {@code id}. Also
 * reads the authoritative inputs from the same schema: the {@code kf_audit_event} targets to derive
 * from, and the {@code usr}/{@code account}/{@code entitlement} name→id maps to resolve against.
 * All reads are absent-table tolerant (empty result), so it never fails on a not-yet-loaded source.
 */
public class EventLinkRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    /** One AuditEvent reference to derive a link from: its canonical id and its raw target. */
    public record AuditTargetRef(String auditId, String target) {
    }

    private final String schema;
    private final String table;

    public EventLinkRepository() {
        this(DEFAULT_SCHEMA);
    }

    public EventLinkRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_event_link";
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    public String auditTable() {
        return schema + ".kf_audit_event";
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id uuid PRIMARY KEY, "
                    + "audit_event_id uuid NOT NULL, "
                    + "source_object_type text, "
                    + "target_object_type text, "
                    + "target_object_id uuid, "
                    + "target_raw text NOT NULL, "
                    + "target_type_hint text, "
                    + "link_status text NOT NULL, "
                    + "resolution_rule text, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, EventLinkRow r) throws SQLException {
        String sql = "INSERT INTO " + table + " (id, audit_event_id, source_object_type, target_object_type, "
                + "target_object_id, target_raw, target_type_hint, link_status, resolution_rule) "
                + "VALUES (?::uuid,?::uuid,?,?,?::uuid,?,?,?,?) "
                + "ON CONFLICT (id) DO UPDATE SET audit_event_id=EXCLUDED.audit_event_id, "
                + "source_object_type=EXCLUDED.source_object_type, target_object_type=EXCLUDED.target_object_type, "
                + "target_object_id=EXCLUDED.target_object_id, target_raw=EXCLUDED.target_raw, "
                + "target_type_hint=EXCLUDED.target_type_hint, link_status=EXCLUDED.link_status, "
                + "resolution_rule=EXCLUDED.resolution_rule, extracted_at=now() "
                + "RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.id());
            ps.setString(2, r.auditEventId());
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

    /** All (auditid, target) pairs from {@code kf_audit_event} (empty if the table is absent). */
    public List<AuditTargetRef> readAuditTargets(Connection conn) throws SQLException {
        List<AuditTargetRef> refs = new ArrayList<>();
        if (!tableExists(conn, auditTable())) {
            return refs;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT auditid::text, target FROM " + auditTable())) {
            while (rs.next()) {
                refs.add(new AuditTargetRef(rs.getString(1), rs.getString(2)));
            }
        }
        return refs;
    }

    public Map<String, Set<String>> loadIdentityNameMap(Connection conn) throws SQLException {
        return loadNameMap(conn, schema + ".kf_identity", "userid", "username", "displayname");
    }

    public Map<String, Set<String>> loadAccountNameMap(Connection conn) throws SQLException {
        return loadNameMap(conn, schema + ".kf_account", "accountid", "native_identity", "account_display_name");
    }

    public Map<String, Set<String>> loadEntitlementNameMap(Connection conn) throws SQLException {
        return loadNameMap(conn, schema + ".kf_entitlement", "entitlementid", "value", "displayable_name");
    }

    /**
     * Builds a name→ids map for an entity, unioning two name columns. A name that maps to more than
     * one distinct id is retained as such so the resolver can flag it ambiguous rather than guess.
     */
    private Map<String, Set<String>> loadNameMap(Connection conn, String qualifiedTable, String idColumn,
                                                 String nameColumnA, String nameColumnB) throws SQLException {
        Map<String, Set<String>> map = new HashMap<>();
        if (!tableExists(conn, qualifiedTable)) {
            return map;
        }
        String sql = "SELECT " + idColumn + "::text, " + nameColumnA + ", " + nameColumnB
                + " FROM " + qualifiedTable;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id == null) {
                    continue;
                }
                addName(map, rs.getString(2), id);
                addName(map, rs.getString(3), id);
            }
        }
        return map;
    }

    private static void addName(Map<String, Set<String>> map, String name, String id) {
        if (name == null || name.isBlank()) {
            return;
        }
        map.computeIfAbsent(name, k -> new HashSet<>()).add(id);
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
