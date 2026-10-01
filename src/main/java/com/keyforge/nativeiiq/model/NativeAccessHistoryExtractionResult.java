package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory output of one native Access-History extraction run — the three immutable Access-History object
 * types, each with its live source {@code countObjects} total so a caller can guard a complete scan. Produced
 * by {@code NativeAccessHistoryExtractionService} inside the IIQ runtime; the IIQ→downstream-DB transport is
 * a separate phase (the native extractor never assumes it can reach the target database itself).
 */
public final class NativeAccessHistoryExtractionResult {

    private final String sourceSystem;
    private final String extractionRunId;
    private final Instant startedAt;
    private Instant finishedAt;

    private int entitlementCaptureSourceCount = -1;
    private int identityEventSourceCount = -1;
    private int certificationSourceCount = -1;

    private final List<NativeHistEntitlementCaptureRow> entitlementCaptures =
            new ArrayList<NativeHistEntitlementCaptureRow>();
    private final List<NativeHistIdentityEventRow> identityEvents =
            new ArrayList<NativeHistIdentityEventRow>();
    private final List<NativeHistCertificationRow> certifications =
            new ArrayList<NativeHistCertificationRow>();

    public NativeAccessHistoryExtractionResult(String sourceSystem, String extractionRunId, Instant startedAt) {
        this.sourceSystem = sourceSystem;
        this.extractionRunId = extractionRunId;
        this.startedAt = startedAt;
    }

    public String getSourceSystem() { return sourceSystem; }
    public String getExtractionRunId() { return extractionRunId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { this.finishedAt = v; }

    public int getEntitlementCaptureSourceCount() { return entitlementCaptureSourceCount; }
    public void setEntitlementCaptureSourceCount(int v) { this.entitlementCaptureSourceCount = v; }
    public int getIdentityEventSourceCount() { return identityEventSourceCount; }
    public void setIdentityEventSourceCount(int v) { this.identityEventSourceCount = v; }
    public int getCertificationSourceCount() { return certificationSourceCount; }
    public void setCertificationSourceCount(int v) { this.certificationSourceCount = v; }

    public List<NativeHistEntitlementCaptureRow> getEntitlementCaptures() { return entitlementCaptures; }
    public List<NativeHistIdentityEventRow> getIdentityEvents() { return identityEvents; }
    public List<NativeHistCertificationRow> getCertifications() { return certifications; }

    public int getEntitlementCaptureCount() { return entitlementCaptures.size(); }
    public int getIdentityEventCount() { return identityEvents.size(); }
    public int getCertificationCount() { return certifications.size(); }
    public int getTotalCount() {
        return entitlementCaptures.size() + identityEvents.size() + certifications.size();
    }
}
