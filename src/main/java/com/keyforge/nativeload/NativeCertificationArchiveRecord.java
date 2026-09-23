package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native CertificationArchive row pulled from the plugin endpoint and prepared for append into
 * {@code iiq_native.kf_certification_archive}. Scalars held directly; the child-certification id list is
 * carried as a pre-serialized JSON string destined for the {@code jsonb} column, and the historical archive
 * XML as a plain text blob. Pure data holder.
 *
 * <p>These records are immutable CEC/history rows: the archive {@code sourceId} is the canonical identity
 * and {@code created} is the authoritative historical archival timestamp (mirrored into {@code srcEventTs}).
 */
public final class NativeCertificationArchiveRecord {

    String sourceId;
    String name;
    String certificationId;
    String certificationGroupId;
    String creatorName;
    String ownerName;
    String comments;

    // nested (jsonb) — child certification ids as a JSON array string
    String childCertificationIdsJson;

    // historical evidence (text)
    String archiveXml;

    // timestamps
    Instant signed;
    Instant expiration;
    Instant created;
    Instant modified;

    // CEC lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String srcNaturalKey;
    Instant srcEventTs;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public Instant getSrcEventTs() { return srcEventTs; }
    public String getSrcObjectId() { return sourceId; }
    public String getSrcObjectType() { return srcObjectType; }
    public String getExtractionRunId() { return extractionRunId; }
    public String getSrcSystem() { return srcSystem; }
    public String getSrcInterface() { return srcInterface; }
}
