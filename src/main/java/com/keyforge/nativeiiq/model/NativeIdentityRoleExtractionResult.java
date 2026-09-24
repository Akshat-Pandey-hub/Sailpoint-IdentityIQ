package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output abstraction of a native identity-role extraction run. Rows are per-edge, but the paginated source
 * unit is the {@code Identity}, so this carries {@code identityCount} (identities in this page) and the
 * authoritative total {@code sourceCount} ({@code countObjects(Identity.class)}) for the importer's guard.
 */
public final class NativeIdentityRoleExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private int sourceCount = -1;
    private int identityCount = 0;
    private final List<NativeIdentityRoleRow> rows = new ArrayList<NativeIdentityRoleRow>();

    public NativeIdentityRoleExtractionResult(String sourceSystem, String extractionRunId,
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
    public int getIdentityCount() { return identityCount; }
    public void setIdentityCount(int v) { this.identityCount = v; }
    public List<NativeIdentityRoleRow> getRows() { return rows; }
    public int getCount() { return rows.size(); }
}
