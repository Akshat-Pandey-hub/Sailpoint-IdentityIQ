package com.keyforge.iiq.canonical;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Maintains the derived {@code kf_identity_account} relationship <b>view</b>.
 *
 * <p>History: the four core entities are now created as PHYSICAL canonical tables by the normal
 * persistence path ({@code kf_identity}, {@code kf_account}, {@code kf_application},
 * {@code kf_entitlement}). This class therefore no longer creates canonical views for those four —
 * doing so would collide with the physical tables. Its remaining job is the one canonical entity
 * that has no independently-extracted source: {@code kf_identity_account}, a pure projection of the
 * account→identity soft reference.
 *
 * <p>The view is built over whichever <b>physical</b> tables actually hold the data, so it works in
 * both a fresh canonical schema (tables {@code kf_account}/{@code kf_identity}) and the legacy
 * validated schema (tables {@code account}/{@code usr}); column names on those physical tables are
 * unchanged by the naming correction ({@code userid}, {@code accountid}, …), so resolution behavior
 * is preserved exactly.
 */
public class CanonicalViewRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    // Canonical core entities (now physical tables) — reported on, never created here.
    public static final String[] CORE_CANONICAL = {"kf_identity", "kf_account", "kf_application", "kf_entitlement"};
    // The one derived relationship view this command maintains.
    public static final String V_IDENTITY_ACCOUNT = "kf_identity_account";

    private final String schema;

    public CanonicalViewRepository() {
        this(DEFAULT_SCHEMA);
    }

    public CanonicalViewRepository(String schema) {
        this.schema = SchemaName.validate(schema);
    }

    public String schema() {
        return schema;
    }

    public String qualified(String name) {
        return schema + "." + name;
    }

    // --- pure SQL builder (unit-testable) -----------------------------------

    /**
     * {@code kf_identity_account} over the given physical account and identity tables. The first-class
     * identity→account edge: deterministic id, both raw ids preserved, and a resolution_status that
     * keeps unresolved references (UNRESOLVED / NO_IDENTITY_REF) rather than discarding them. No
     * identity is manufactured.
     */
    public String identityAccountViewSql(String accountTable, String identityTable) {
        return "CREATE OR REPLACE VIEW " + qualified(V_IDENTITY_ACCOUNT) + " AS "
                + "SELECT md5('kf_identity_account|' || ac.accountid::text)::uuid AS id, "
                + "ac.userid AS identity_id, ac.accountid AS account_id, "
                + "ac.native_identity AS account_native_name, ac.identity_display_name, "
                + "u.userid AS resolved_identity_id, "
                + "CASE WHEN ac.userid IS NULL THEN 'NO_IDENTITY_REF' "
                + "WHEN u.userid IS NULL THEN 'UNRESOLVED' ELSE 'RESOLVED' END AS resolution_status "
                + "FROM " + qualified(accountTable) + " ac "
                + "LEFT JOIN " + qualified(identityTable) + " u ON u.userid = ac.userid";
    }

    // --- DB helpers ---------------------------------------------------------

    public void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    public void ensureSchema(Connection conn) throws SQLException {
        execute(conn, "CREATE SCHEMA IF NOT EXISTS " + schema);
    }

    /**
     * The pg_class relkind of {@code schema.name}: 'r' table, 'v' view, 'm' matview, 'p' partitioned,
     * or {@code null} if it does not exist.
     */
    public Character relationKind(Connection conn, String name) throws SQLException {
        String sql = "SELECT c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String k = rs.getString(1);
                return k == null || k.isEmpty() ? null : k.charAt(0);
            }
        }
    }

    public boolean isPhysicalTable(Connection conn, String name) throws SQLException {
        Character k = relationKind(conn, name);
        return k != null && (k == 'r' || k == 'p');
    }

    /**
     * Returns the physical table holding this entity: the canonical name if it is a physical table,
     * else the legacy name if it is, else {@code null}. Lets the derived view attach to real columns
     * in either a fresh canonical schema or the legacy validated schema.
     */
    public String resolvePhysicalTable(Connection conn, String canonical, String legacy) throws SQLException {
        if (isPhysicalTable(conn, canonical)) {
            return canonical;
        }
        if (isPhysicalTable(conn, legacy)) {
            return legacy;
        }
        return null;
    }

    public long count(Connection conn, String name) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + qualified(name))) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public long scalarLong(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
