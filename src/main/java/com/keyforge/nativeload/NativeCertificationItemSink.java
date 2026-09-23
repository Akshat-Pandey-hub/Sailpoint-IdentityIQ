package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native CertificationItem rows. Production impl: {@link JdbcNativeCertificationItemSink}. */
public interface NativeCertificationItemSink {

    void ensure() throws SQLException;

    NativeCertificationItemRepository.UpsertOutcome upsert(NativeCertificationItemRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
