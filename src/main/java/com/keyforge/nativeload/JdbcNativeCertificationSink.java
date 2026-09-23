package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** JDBC-backed {@link NativeCertificationSink}: writes to kf_certification and runs the deletion sweep. */
public final class JdbcNativeCertificationSink implements NativeCertificationSink {

    private final Connection conn;
    private final NativeCertificationRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeCertificationSink(Connection conn, NativeCertificationRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeCertificationRepository.UpsertOutcome upsert(NativeCertificationRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_certification", "certificationid", keepIds);
    }
}
