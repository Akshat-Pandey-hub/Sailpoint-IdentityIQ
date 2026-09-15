package com.keyforge.iiq.provisioningtransaction;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Plain-JDBC data access for {@code kf_provisioning_item}. Explicit DDL inside {@code PG_SCHEMA};
 * idempotent upsert on {@code itemid}. {@code txnid} references the parent {@code kf_provisioning_txn}
 * row (same canonical UUID); {@code error_messages} is {@code jsonb}. Independent of the transaction
 * extractor — it creates its own table and never alters {@code kf_provisioning_txn}.
 */
public class ProvisioningItemRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;

    public ProvisioningItemRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ProvisioningItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_provisioning_item";
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
                    + "itemid uuid PRIMARY KEY, "
                    + "txnid uuid, "
                    + "source_txn_id text, "
                    + "request_type text, "
                    + "item_index integer, "
                    + "operation text, "
                    + "name text, "
                    + "value text, "
                    + "result text, "
                    + "reason text, "
                    + "is_attribute_request boolean, "
                    + "error_messages jsonb, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, ProvisioningItemRow r) throws SQLException {
        String sql = "INSERT INTO " + targetTable + " (itemid, txnid, source_txn_id, request_type, item_index, "
                + "operation, name, value, result, reason, is_attribute_request, error_messages) "
                + "VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?::jsonb) "
                + "ON CONFLICT (itemid) DO UPDATE SET txnid=EXCLUDED.txnid, source_txn_id=EXCLUDED.source_txn_id, "
                + "request_type=EXCLUDED.request_type, item_index=EXCLUDED.item_index, operation=EXCLUDED.operation, "
                + "name=EXCLUDED.name, value=EXCLUDED.value, result=EXCLUDED.result, reason=EXCLUDED.reason, "
                + "is_attribute_request=EXCLUDED.is_attribute_request, error_messages=EXCLUDED.error_messages, "
                + "extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.itemid());
            ps.setString(i++, r.txnid());
            ps.setString(i++, r.sourceTxnId());
            ps.setString(i++, r.requestType());
            ps.setInt(i++, r.itemIndex());
            ps.setString(i++, r.operation());
            ps.setString(i++, r.name());
            ps.setString(i++, r.value());
            ps.setString(i++, r.result());
            ps.setString(i++, r.reason());
            setBool(ps, i++, r.attributeRequest());
            ps.setString(i++, r.errorMessagesJson());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BOOLEAN); else ps.setBoolean(i, v);
    }
}
