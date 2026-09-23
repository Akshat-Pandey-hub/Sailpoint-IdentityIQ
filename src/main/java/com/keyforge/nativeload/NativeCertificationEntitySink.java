package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native CertificationEntity rows. Production impl: {@link JdbcNativeCertificationEntitySink}. */
public interface NativeCertificationEntitySink {

    void ensure() throws SQLException;

    NativeCertificationEntityRepository.UpsertOutcome upsert(NativeCertificationEntityRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
