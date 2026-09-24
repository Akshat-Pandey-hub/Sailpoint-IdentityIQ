package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativePolicySink}: writes to {@code iiq_native.kf_policy} and runs the current-state
 * deletion sweep via {@link SoftDeleteSweeper}. Only class in the policy import path that touches SQL.
 */
public final class JdbcNativePolicySink implements NativePolicySink {

    private final Connection conn;
    private final NativePolicyRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativePolicySink(Connection conn, NativePolicyRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativePolicyRepository.UpsertOutcome upsert(NativePolicyRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepPolicyIds) throws SQLException {
        return sweeper.sweep(conn, "kf_policy", "policyid", keepPolicyIds);
    }
}
