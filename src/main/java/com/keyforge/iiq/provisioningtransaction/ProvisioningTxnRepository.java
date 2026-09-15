package com.keyforge.iiq.provisioningtransaction;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the canonical {@code kf_provisioning_txn} table. Explicit DDL inside
 * {@code PG_SCHEMA}; idempotent upsert on {@code txnid}. Independent of the kf_event_link path (that
 * remains unchanged). {@code created_at}/{@code modified_at}/{@code last_retry} are {@code timestamp}
 * WITHOUT time zone: the source values are naive display strings with no zone/seconds, so a
 * {@code timestamptz} would fabricate a zone; the verbatim source string is kept in
 * {@code created_display}.
 */
public class ProvisioningTxnRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String table;

    public ProvisioningTxnRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ProvisioningTxnRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_provisioning_txn";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return table;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "txnid uuid PRIMARY KEY, "
                    + "source_id text, "
                    + "name text, "
                    + "operation text, "
                    + "source text, "
                    + "status text, "
                    + "status_message text, "
                    + "type text, "
                    + "type_message text, "
                    + "integration text, "
                    + "identity_name text, "
                    + "identity_display_name text, "
                    + "application_name text, "
                    + "native_identity text, "
                    + "account_display_name text, "
                    + "created_display text, "
                    + "created_at timestamp, "
                    + "modified_at timestamp, "
                    + "last_retry timestamp, "
                    + "ticket_id text, "
                    + "retry boolean, "
                    + "retry_count integer, "
                    + "timed_out boolean, "
                    + "forced boolean, "
                    + "forceable boolean, "
                    + "result text, "
                    + "access_request_id text, "
                    + "certification_name text, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, ProvisioningTxnRow r) throws SQLException {
        String sql = "INSERT INTO " + table + " (txnid, source_id, name, operation, source, status, status_message, "
                + "type, type_message, integration, identity_name, identity_display_name, application_name, "
                + "native_identity, account_display_name, created_display, created_at, modified_at, last_retry, "
                + "ticket_id, retry, retry_count, timed_out, forced, forceable, result, access_request_id, "
                + "certification_name) VALUES (?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (txnid) DO UPDATE SET source_id=EXCLUDED.source_id, name=EXCLUDED.name, "
                + "operation=EXCLUDED.operation, source=EXCLUDED.source, status=EXCLUDED.status, "
                + "status_message=EXCLUDED.status_message, type=EXCLUDED.type, type_message=EXCLUDED.type_message, "
                + "integration=EXCLUDED.integration, identity_name=EXCLUDED.identity_name, "
                + "identity_display_name=EXCLUDED.identity_display_name, application_name=EXCLUDED.application_name, "
                + "native_identity=EXCLUDED.native_identity, account_display_name=EXCLUDED.account_display_name, "
                + "created_display=EXCLUDED.created_display, created_at=EXCLUDED.created_at, "
                + "modified_at=EXCLUDED.modified_at, last_retry=EXCLUDED.last_retry, ticket_id=EXCLUDED.ticket_id, "
                + "retry=EXCLUDED.retry, retry_count=EXCLUDED.retry_count, timed_out=EXCLUDED.timed_out, "
                + "forced=EXCLUDED.forced, forceable=EXCLUDED.forceable, result=EXCLUDED.result, "
                + "access_request_id=EXCLUDED.access_request_id, certification_name=EXCLUDED.certification_name, "
                + "extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.txnid());
            ps.setString(i++, r.sourceId());
            ps.setString(i++, r.name());
            ps.setString(i++, r.operation());
            ps.setString(i++, r.source());
            ps.setString(i++, r.status());
            ps.setString(i++, r.statusMessage());
            ps.setString(i++, r.type());
            ps.setString(i++, r.typeMessage());
            ps.setString(i++, r.integration());
            ps.setString(i++, r.identityName());
            ps.setString(i++, r.identityDisplayName());
            ps.setString(i++, r.applicationName());
            ps.setString(i++, r.nativeIdentity());
            ps.setString(i++, r.accountDisplayName());
            ps.setString(i++, r.createdDisplay());
            setTs(ps, i++, r.createdAt());
            setTs(ps, i++, r.modifiedAt());
            setTs(ps, i++, r.lastRetry());
            ps.setString(i++, r.ticketId());
            setBool(ps, i++, r.retry());
            setInt(ps, i++, r.retryCount());
            setBool(ps, i++, r.timedOut());
            setBool(ps, i++, r.forced());
            setBool(ps, i++, r.forceable());
            ps.setString(i++, r.result());
            ps.setString(i++, r.accessRequestId());
            ps.setString(i++, r.certificationName());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTs(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setObject(i, v);
    }

    private static void setBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BOOLEAN); else ps.setBoolean(i, v);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }
}
