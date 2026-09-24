package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Output abstraction of a native IdentityRequest extraction run (carries source count + guard). */
public final class NativeIdentityRequestExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private int sourceCount = -1;
    private final List<NativeIdentityRequestRow> rows = new ArrayList<NativeIdentityRequestRow>();

    public NativeIdentityRequestExtractionResult(String sourceSystem, String extractionRunId,
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
    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int v) { this.sourceCount = v; }
    public List<NativeIdentityRequestRow> getRows() { return rows; }
    public int getCount() { return rows.size(); }
}
