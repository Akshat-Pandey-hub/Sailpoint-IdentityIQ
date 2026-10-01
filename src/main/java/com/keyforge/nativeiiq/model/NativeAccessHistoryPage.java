package com.keyforge.nativeiiq.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a single native Access-History object type: the rows plus the live total source count
 * ({@code countObjects}) so the loader can guard a complete scan. Used by the plugin resource endpoints
 * (one type per endpoint), mirroring the per-entity paging of the other native {@code *-db} extractors.
 */
public final class NativeAccessHistoryPage<R> {

    private final String sourceSystem;
    private final String extractionRunId;
    private final int sourceCount;
    private final List<R> rows;

    public NativeAccessHistoryPage(String sourceSystem, String extractionRunId, int sourceCount, List<R> rows) {
        this.sourceSystem = sourceSystem;
        this.extractionRunId = extractionRunId;
        this.sourceCount = sourceCount;
        this.rows = rows == null ? new ArrayList<R>() : rows;
    }

    public String getSourceSystem() { return sourceSystem; }
    public String getExtractionRunId() { return extractionRunId; }
    public int getSourceCount() { return sourceCount; }
    public List<R> getRows() { return rows; }
    public int getReturned() { return rows.size(); }
}
