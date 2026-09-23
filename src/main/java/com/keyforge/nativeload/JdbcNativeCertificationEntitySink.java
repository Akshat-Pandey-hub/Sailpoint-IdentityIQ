package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** JDBC-backed {@link NativeCertificationEntitySink}: writes to kf_certification_entity and runs the deletion sweep. */
public final class JdbcNativeCertificationEntitySink implements NativeCertificationEntitySink {

    private final Connection conn;
    private final NativeCertificationEntityRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeCertificationEntitySink(Connection conn, NativeCertificationEntityRepository repository,
                                             String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeCertificationEntityRepository.UpsertOutcome upsert(NativeCertificationEntityRecord record)
            throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_certification_entity", "certificationentityid", keepIds);
    }
}
