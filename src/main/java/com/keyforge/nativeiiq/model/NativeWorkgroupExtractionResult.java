package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The output abstraction of a native Workgroup extraction run — the extractor returns this instead of
 * persisting directly. Pure data holder.
 */
public final class NativeWorkgroupExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<NativeWorkgroupRow> workgroups = new ArrayList<NativeWorkgroupRow>();

    public NativeWorkgroupExtractionResult(String sourceSystem, String extractionRunId,
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

    public List<NativeWorkgroupRow> getWorkgroups() { return workgroups; }
    public int getCount() { return workgroups.size(); }
}
