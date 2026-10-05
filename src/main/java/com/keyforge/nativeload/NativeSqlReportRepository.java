package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Plain-JDBC persistence for the entitlement-assignment SQL report into
 * {@code <schema>.kf_entitlement_assignment}. This table is the <b>result of the business SQL</b>, not a
 * redesign of SailPoint's source tables: its columns are exactly the query's {@code SELECT} aliases,
 * stored as {@code text} so every value is preserved verbatim and SQL {@code NULL} stays {@code NULL}
 * (no dialect/type assumptions about the IIQ database the query ran against).
 *
 * <p>Load semantics: <b>replace-all per run</b> inside one transaction (DELETE then batch INSERT), so the
 * table always equals the latest query result and a re-run is idempotent. The query is already
 * {@code SELECT DISTINCT}, so no row-level dedup is needed.
 */
public final class NativeSqlReportRepository {

    /** The 29 business columns = the query's SELECT aliases, in order. Stored as text. */
    static final List<String> COLUMNS = List.of(
            "identity_id", "user_id", "user_name", "application_name", "account_name",
            "entitlement_attribute", "entitlement_value", "assigned", "granted_by_role",
            "entitlement_source", "entitlement_assignment_id", "entitlement_assigner",
            "entitlement_created", "entitlement_modified", "role_id", "role_name", "role_display_name",
            "role_type", "role_requestable", "identity_request_id", "access_request_id", "request_type",
            "requested_by", "request_date", "request_status", "request_operation", "provisioning_state",
            "assignment_type", "assignment_reason");

    private final String schema;
    private final String targetTable;
    private final List<String> columns;

    public NativeSqlReportRepository(String schema) {
        this(schema, "kf_entitlement_assignment", COLUMNS);
    }

    public NativeSqlReportRepository(String schema, String table) {
        this(schema, table, COLUMNS);
    }

    /**
     * Columns-parameterized variant so a second baked report (e.g. the workgroup-members query) can
     * reuse this exact replace-all/idempotency machinery with its own SELECT aliases and destination
     * table. {@code columns} must equal that query's SELECT aliases, in order. Query 1 continues to use
     * the {@link #COLUMNS} default via the other constructors — its behaviour is unchanged.
     */
    public NativeSqlReportRepository(String schema, String table, List<String> columns) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + "." + table;
        this.columns = columns;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS " + targetTable + " (");
        ddl.append("id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, ");
        for (String col : columns) {
            ddl.append(col).append(" text, ");
        }
        ddl.append("source_system text, ");
        ddl.append("source_interface text, ");
        ddl.append("extraction_run_id text, ");
        ddl.append("extracted_at timestamptz NOT NULL DEFAULT now())");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute(ddl.toString());
        }
    }

    /**
     * Replaces the entire table with {@code rows} in one transaction (DELETE + batch INSERT). Returns the
     * number of rows inserted. {@code source_interface} is stamped {@code native_iiq_jdbc} to distinguish
     * this SQL-result path from the object-model {@code native_iiq_java_api} extractors.
     */
    public int replaceAll(Connection conn, List<Map<String, String>> rows, String runId) throws SQLException {
        ensureTargetTable(conn);

        StringBuilder cols = new StringBuilder();
        StringBuilder qs = new StringBuilder();
        for (String c : columns) {
            cols.append(c).append(", ");
            qs.append("?, ");
        }
        String insertSql = "INSERT INTO " + targetTable + " (" + cols + "source_system, source_interface, extraction_run_id) "
                + "VALUES (" + qs + "?, ?, ?)";

        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (Statement del = conn.createStatement()) {
                del.execute("DELETE FROM " + targetTable);
            }
            int inserted = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map<String, String> row : rows) {
                    int i = 1;
                    for (String c : columns) {
                        ps.setString(i++, row.get(c)); // null stays SQL NULL
                    }
                    ps.setString(i++, "IdentityIQ");
                    ps.setString(i++, "native_iiq_jdbc");
                    ps.setString(i, runId);
                    ps.addBatch();
                    inserted++;
                    if (inserted % 1000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
            return inserted;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }
}
