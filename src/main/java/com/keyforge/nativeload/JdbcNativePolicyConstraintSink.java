package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativePolicyConstraintSink}: writes to {@code iiq_native.kf_policy_constraint} and
 * runs the current-state deletion sweep via {@link SoftDeleteSweeper} (a constraint removed from a policy
 * leaves the active set). Only class in the import path that touches SQL.
 */
public final class JdbcNativePolicyConstraintSink implements NativePolicyConstraintSink {

    private final Connection conn;
    private final NativePolicyConstraintRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativePolicyConstraintSink(Connection conn, NativePolicyConstraintRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativePolicyConstraintRepository.UpsertOutcome upsert(NativePolicyConstraintRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepConstraintIds) throws SQLException {
        return sweeper.sweep(conn, "kf_policy_constraint", "policyconstraintid", keepConstraintIds);
    }
}
