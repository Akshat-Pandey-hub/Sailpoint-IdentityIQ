package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code accesshistory.HistoricalCertification} — an immutable
 * historical record of a certification a HistoricalIdentity was part of. Field-complete over the class
 * (which extends {@code SailPointObject} directly: id/name/created/modified plus the cert coordinates,
 * sign-off dates and raw JSON). NULLs preserved. Pure data holder.
 */
public final class NativeHistCertificationRow {

    private String sourceId;
    private String name;
    private String certId;
    private String certType;
    private String certName;
    private String certDisplayName;
    private Instant finished;
    private Instant signed;
    private String certJson;
    private Instant created;
    private Instant modified;

    // lineage
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.accesshistory.HistoricalCertification";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCertId() { return certId; }
    public void setCertId(String v) { this.certId = v; }
    public String getCertType() { return certType; }
    public void setCertType(String v) { this.certType = v; }
    public String getCertName() { return certName; }
    public void setCertName(String v) { this.certName = v; }
    public String getCertDisplayName() { return certDisplayName; }
    public void setCertDisplayName(String v) { this.certDisplayName = v; }
    public Instant getFinished() { return finished; }
    public void setFinished(Instant v) { this.finished = v; }
    public Instant getSigned() { return signed; }
    public void setSigned(Instant v) { this.signed = v; }
    public String getCertJson() { return certJson; }
    public void setCertJson(String v) { this.certJson = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }
    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }
    public String getSrcInterface() { return srcInterface; }
    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }
    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }
    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
