package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native Workgroup rows — separates orchestration from JDBC so the orchestration
 * is unit-testable with a fake sink. Production impl: {@link JdbcNativeWorkgroupSink}.
 */
public interface NativeWorkgroupSink {

    void ensure() throws SQLException;

    NativeWorkgroupRepository.UpsertOutcome upsert(NativeWorkgroupRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepWorkgroupIds) throws SQLException;
}
