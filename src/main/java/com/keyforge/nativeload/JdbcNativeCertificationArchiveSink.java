package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC-backed {@link NativeCertificationArchiveSink}: appends to {@code iiq_native.kf_certification_archive}
 * via {@link NativeCertificationArchiveRepository}. The only class in the CertificationArchive import path
 * that touches SQL. Append-only — no deletion sweep (archives are immutable CEC/history records).
 */
public final class JdbcNativeCertificationArchiveSink implements NativeCertificationArchiveSink {

    private final Connection conn;
    private final NativeCertificationArchiveRepository repository;

    public JdbcNativeCertificationArchiveSink(Connection conn, NativeCertificationArchiveRepository repository) {
        this.conn = conn;
        this.repository = repository;
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeCertificationArchiveRepository.AppendOutcome append(NativeCertificationArchiveRecord record)
            throws SQLException {
        return repository.append(conn, record);
    }
}
