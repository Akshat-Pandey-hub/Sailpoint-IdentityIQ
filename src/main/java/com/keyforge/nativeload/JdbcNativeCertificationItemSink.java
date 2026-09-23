package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** JDBC-backed {@link NativeCertificationItemSink}: writes to kf_certification_item and runs the sweep. */
public final class JdbcNativeCertificationItemSink implements NativeCertificationItemSink {

    private final Connection conn;
    private final NativeCertificationItemRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeCertificationItemSink(Connection conn, NativeCertificationItemRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeCertificationItemRepository.UpsertOutcome upsert(NativeCertificationItemRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_certification_item", "certificationitemid", keepIds);
    }
}
