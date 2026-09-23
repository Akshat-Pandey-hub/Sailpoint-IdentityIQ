package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output abstraction of a native IdentityEntitlement extraction run — the extractor returns this instead
 * of persisting directly. Carries the authoritative source count ({@code countObjects}) for reconciliation
 * and the empty/incomplete-scan safety guard. Pure data holder.
 */
public final class NativeIdentityEntitlementExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private int sourceCount = -1;
    private final List<NativeIdentityEntitlementRow> rows = new ArrayList<NativeIdentityEntitlementRow>();

    public NativeIdentityEntitlementExtractionResult(String sourceSystem, String extractionRunId,
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

    public List<NativeIdentityEntitlementRow> getRows() { return rows; }
    public int getCount() { return rows.size(); }
}
