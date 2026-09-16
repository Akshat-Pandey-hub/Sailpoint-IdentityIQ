package com.keyforge.iiq.countrecon;

import java.util.List;

/**
 * Catalog of domains extracted by both the PostgreSQL and Parquet pipelines, plus the pure count
 * comparison logic. This is the PDF's count / cross-pipeline reconciliation: it verifies the two
 * stores agree, surfacing drift or data loss between them. Table and dataset names are the real ones
 * (verified against the repository DDLs and the Parquet dataset registry); nothing is inferred.
 */
public final class CountReconciliation {

    public static final String MATCH = "MATCH";
    public static final String MISMATCH = "MISMATCH";
    public static final String PG_ONLY = "PG_ONLY";
    public static final String PARQUET_ONLY = "PARQUET_ONLY";
    public static final String BOTH_ABSENT = "BOTH_ABSENT";

    private CountReconciliation() {
    }

    /** The 9 domains present in both stores. (Certifications differ per store, so they are not paired.) */
    public static List<CountPair> all() {
        return List.of(
                new CountPair("task_result", "kf_task_result", "task_result"),
                new CountPair("audit_event", "kf_audit_event", "kf_audit_event"),
                new CountPair("access_request", "kf_access_request", "kf_access_request"),
                new CountPair("request_item", "kf_request_item", "kf_request_item"),
                new CountPair("request_approval", "kf_request_approval", "kf_request_approval"),
                new CountPair("provisioning_txn", "kf_provisioning_txn", "kf_provisioning_txn"),
                new CountPair("provisioning_item", "kf_provisioning_item", "kf_provisioning_item"),
                new CountPair("violation", "kf_violation", "kf_violation"),
                new CountPair("event_link", "kf_event_link", "kf_event_link"));
    }

    /**
     * Classifies a comparison. A count of {@code -1} means the store did not have the table/dataset at
     * all (distinct from a real count of 0).
     */
    public static String classify(long pgCount, long parquetCount) {
        boolean pgAbsent = pgCount < 0;
        boolean pqAbsent = parquetCount < 0;
        if (pgAbsent && pqAbsent) {
            return BOTH_ABSENT;
        }
        if (pqAbsent) {
            return PG_ONLY;
        }
        if (pgAbsent) {
            return PARQUET_ONLY;
        }
        return pgCount == parquetCount ? MATCH : MISMATCH;
    }

    /** Count of a PostgreSQL table (schema validated upstream; no value parameters). */
    public static String pgCountSql(String schema, String table) {
        return "SELECT count(*) FROM " + schema + "." + table;
    }

    /**
     * Active-row count for a table that carries the soft-delete flag: soft-deleted rows are excluded so
     * the count reflects current state (and matches the latest Parquet snapshot). Used only when the
     * {@code is_deleted} column exists; other tables keep {@link #pgCountSql}.
     */
    public static String pgActiveCountSql(String schema, String table) {
        return "SELECT count(*) FROM " + schema + "." + table + " WHERE is_deleted IS NOT TRUE";
    }
}
