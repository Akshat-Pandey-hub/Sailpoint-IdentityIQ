package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The well-defined output abstraction of a native extraction run. The extractor returns this instead
 * of persisting directly — the IIQ→DB transport is a later phase. Pure data holder.
 */
public final class NativeExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<NativeIdentityRow> identities = new ArrayList<NativeIdentityRow>();

    public NativeExtractionResult(String sourceSystem, String extractionRunId, String entityType, Instant startedAt) {
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

    public List<NativeIdentityRow> getIdentities() { return identities; }
    public int getCount() { return identities.size(); }
}
