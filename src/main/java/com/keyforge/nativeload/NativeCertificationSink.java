package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native Certification rows. Production impl: {@link JdbcNativeCertificationSink}. */
public interface NativeCertificationSink {

    void ensure() throws SQLException;

    NativeCertificationRepository.UpsertOutcome upsert(NativeCertificationRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
