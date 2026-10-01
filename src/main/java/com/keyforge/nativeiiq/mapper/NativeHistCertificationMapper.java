package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeHistCertificationRow;

import sailpoint.object.accesshistory.HistoricalCertification;

import java.time.Instant;
import java.util.Date;

/**
 * Maps a native {@code sailpoint.object.accesshistory.HistoricalCertification} into a
 * {@link NativeHistCertificationRow}. Read-only; every getter verified against the 8.4 {@code identityiq.jar}
 * (the class extends {@code SailPointObject} directly — id/name/created/modified plus the cert coordinates,
 * sign-off dates and raw JSON). Values verbatim, NULL preserved. No inference.
 */
public final class NativeHistCertificationMapper {

    private NativeHistCertificationMapper() {
    }

    public static NativeHistCertificationRow map(HistoricalCertification h,
                                                 String sourceSystem, String extractionRunId) {
        NativeHistCertificationRow row = new NativeHistCertificationRow();

        row.setSourceId(h.getId());
        row.setName(h.getName());
        row.setCertId(h.getCertId());
        row.setCertType(h.getCertType());
        row.setCertName(h.getCertName());
        row.setCertDisplayName(h.getCertDisplayName());
        row.setFinished(toInstant(h.getFinished()));
        row.setSigned(toInstant(h.getSigned()));
        row.setCertJson(safeJson(h));
        row.setCreated(toInstant(h.getCreated()));
        row.setModified(toInstant(h.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /** Raw stored certification payload, verbatim. Never throws — degrades to null. */
    private static String safeJson(HistoricalCertification h) {
        try {
            return h.getJson();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
