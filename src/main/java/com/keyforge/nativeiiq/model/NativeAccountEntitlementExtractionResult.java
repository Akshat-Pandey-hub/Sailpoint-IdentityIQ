package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output abstraction of a native account-entitlement extraction run over {@code Link}s. Because rows are
 * per-value edges but the paginated <b>source unit is the Link</b>, this result carries both the number of
 * Links in this page ({@code linkCount}) and the authoritative total Link count ({@code sourceCount} via
 * {@code countObjects(Link.class)}) so the importer can page by Link and guard against an incomplete scan.
 * Pure data holder.
 */
public final class NativeAccountEntitlementExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final String entityType;
    private final Instant startedAt;
    private Instant finishedAt;
    private int sourceCount = -1;   // total Links (countObjects)
    private int linkCount = 0;      // Links processed in this page
    private final List<NativeAccountEntitlementRow> rows = new ArrayList<NativeAccountEntitlementRow>();

    public NativeAccountEntitlementExtractionResult(String sourceSystem, String extractionRunId,
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
    public int getLinkCount() { return linkCount; }
    public void setLinkCount(int v) { this.linkCount = v; }

    public List<NativeAccountEntitlementRow> getRows() { return rows; }
    public int getCount() { return rows.size(); }
}
