package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native-source projection of a SailPoint {@code CertificationArchive} — the immutable historical record of a
 * completed/archived certification (the compressed archive XML plus its summary metadata). This is a
 * <b>CEC / history</b> entity, not a current-state one: an archive row is written once when the certification
 * is archived and is never mutated or deleted. Pure data holder (no SailPoint dependency).
 *
 * <p>The archive's own {@code getId()} is the canonical stable identity. {@code getCreated()} is the
 * authoritative historical archival timestamp and is carried as {@code srcEventTs}.
 */
public final class NativeCertificationArchiveRow {

    // --- identity / core ---
    private String sourceId;             // archive getId() — canonical stable identity
    private String name;                 // archive natural key (getName())
    private String certificationId;      // getCertificationId()
    private String certificationGroupId; // getCertificationGroupId()
    private String creatorName;          // getCreatorName()
    private String ownerName;            // getOwnerName()
    private String comments;             // getComments()

    // --- child linkage (jsonb) ---
    private final List<String> childCertificationIds = new ArrayList<String>();

    // --- historical evidence (text) ---
    private String archiveXml;           // getArchive() — the historical certification XML

    // --- source timestamps ---
    private Instant signed;              // getSigned()
    private Instant expiration;          // getExpiration()
    private Instant created;             // getCreated() — authoritative historical archival time (= srcEventTs)
    private Instant modified;            // getModified()

    // --- CEC lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.CertificationArchive";
    private String srcNaturalKey;        // the archive's name
    private Instant srcEventTs;          // = created
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }

    public String getCertificationGroupId() { return certificationGroupId; }
    public void setCertificationGroupId(String v) { this.certificationGroupId = v; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String v) { this.creatorName = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }

    public List<String> getChildCertificationIds() { return childCertificationIds; }

    public String getArchiveXml() { return archiveXml; }
    public void setArchiveXml(String v) { this.archiveXml = v; }

    public Instant getSigned() { return signed; }
    public void setSigned(Instant v) { this.signed = v; }

    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant v) { this.expiration = v; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }

    public String getSrcInterface() { return srcInterface; }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }

    public String getSrcNaturalKey() { return srcNaturalKey; }
    public void setSrcNaturalKey(String v) { this.srcNaturalKey = v; }

    public Instant getSrcEventTs() { return srcEventTs; }
    public void setSrcEventTs(Instant v) { this.srcEventTs = v; }

    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }

    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
