package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native GroupDefinition rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeGroupDefinitionSink}.
 */
public interface NativeGroupDefinitionSink {

    void ensure() throws SQLException;

    NativeGroupDefinitionRepository.UpsertOutcome upsert(NativeGroupDefinitionRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepGroupIds) throws SQLException;
}
