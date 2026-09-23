package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeCertificationArchiveRow;

import sailpoint.object.CertificationArchive;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Maps a native {@code sailpoint.object.CertificationArchive} into a {@link NativeCertificationArchiveRow}.
 * Read-only: only getters verified against the real 8.4 {@code identityiq.jar}. Nothing is inferred — a
 * native {@code null} stays {@code null}. The child-certification id list is copied verbatim.
 *
 * <p><b>CEC semantics:</b> {@code getId()} is the canonical stable identity of the archive (the source PK),
 * and {@code getCreated()} is the authoritative historical archival timestamp, carried as {@code srcEventTs}.
 * These records are immutable once written.
 */
public final class NativeCertificationArchiveMapper {

    private NativeCertificationArchiveMapper() {
    }

    public static NativeCertificationArchiveRow map(CertificationArchive a, String sourceSystem, String extractionRunId) {
        NativeCertificationArchiveRow row = new NativeCertificationArchiveRow();

        row.setSourceId(a.getId());
        row.setName(a.getName());
        row.setCertificationId(a.getCertificationId());
        row.setCertificationGroupId(a.getCertificationGroupId());
        row.setCreatorName(a.getCreatorName());
        row.setOwnerName(a.getOwnerName());
        row.setComments(a.getComments());

        List<String> children = a.getChildCertificationIds();
        if (children != null) {
            for (String c : children) {
                if (c != null) {
                    row.getChildCertificationIds().add(c);
                }
            }
        }

        row.setArchiveXml(a.getArchive());

        row.setSigned(toInstant(a.getSigned()));
        row.setExpiration(toInstant(a.getExpiration()));
        Instant created = toInstant(a.getCreated());
        row.setCreated(created);
        row.setModified(toInstant(a.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setSrcNaturalKey(a.getName());
        row.setSrcEventTs(created);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
