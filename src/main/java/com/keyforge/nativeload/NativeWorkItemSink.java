package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native WorkItem rows. Production impl: {@link JdbcNativeWorkItemSink}. */
public interface NativeWorkItemSink {

    void ensure() throws SQLException;

    NativeWorkItemRepository.UpsertOutcome upsert(NativeWorkItemRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
