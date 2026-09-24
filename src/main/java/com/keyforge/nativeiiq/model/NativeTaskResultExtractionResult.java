package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output abstraction of a native {@code TaskResult} extraction run — the extractor returns this instead
 * of persisting directly. Pure data holder.
 */
public final class NativeTaskResultExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<NativeTaskResultRow> taskResults = new ArrayList<NativeTaskResultRow>();

    public NativeTaskResultExtractionResult(String sourceSystem, String extractionRunId,
                                            String entityType, Instant startedAt) {
        this.sourceSystem = sourceSystem;
        this.extractionRunId = extractionRunId;
        this.entityType = entityType;
        this.startedAt = startedAt;
    }

    public String getSourceSystem() { return sourceSystem; }
    public String getExtractionRunId() { return extractionRunId; }
    public String getEntityType() { return entityType; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { this.finishedAt = v; }

    public List<NativeTaskResultRow> getTaskResults() { return taskResults; }
    public int getCount() { return taskResults.size(); }
}
