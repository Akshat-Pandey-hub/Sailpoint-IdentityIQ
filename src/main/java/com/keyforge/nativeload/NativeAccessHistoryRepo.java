package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Append-only persistence seam shared by the three native Access-History tables. Access-History objects are
 * immutable historical evidence, so every write is {@code INSERT … ON CONFLICT (pk) DO NOTHING} — no update,
 * no delete, no sweep. A generic import loop ({@link NativeAccessHistoryImportService}) drives each endpoint
 * through this interface so the paging / {@code sourceCount} guard is written once.
 */
public interface NativeAccessHistoryRepo {

    /** The IIQ source entity name (for logging / the sourceCount guard message). */
    String entity();

    /** The fully-qualified target table (e.g. {@code iiq_native.kf_access_hist_entitlement}). */
    String targetTable();

    /** Create schema + table if absent. */
    void ensureTargetTable(Connection conn) throws SQLException;

    /** Append one JSON row; returns true if a new row was inserted, false if it already existed (skipped). */
    boolean append(Connection conn, JsonNode row) throws SQLException;
}
