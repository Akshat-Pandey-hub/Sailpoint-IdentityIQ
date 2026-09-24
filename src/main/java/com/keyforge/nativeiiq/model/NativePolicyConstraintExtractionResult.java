package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output of a native policy-constraint extraction page. Carries the total Policy count ({@code sourceCount}
 * via countObjects) and the number of policies covered by this page ({@code policyCount}) so the client can
 * page by parent and guard a complete scan. Pure data holder.
 */
public final class NativePolicyConstraintExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private int sourceCount = -1;
    private int policyCount;
    private final List<NativePolicyConstraintRow> constraints = new ArrayList<NativePolicyConstraintRow>();

    public NativePolicyConstraintExtractionResult(String sourceSystem, String extractionRunId,
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
    public int getPolicyCount() { return policyCount; }
    public void setPolicyCount(int v) { this.policyCount = v; }
    public List<NativePolicyConstraintRow> getConstraints() { return constraints; }
    public int getCount() { return constraints.size(); }
}
