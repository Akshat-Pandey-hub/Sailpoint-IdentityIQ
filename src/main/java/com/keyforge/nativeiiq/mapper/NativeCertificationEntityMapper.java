package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeCertificationEntityRow;

import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Identity;

import java.time.Instant;
import java.util.Date;

/**
 * Maps a native {@code sailpoint.object.CertificationEntity} into a {@link NativeCertificationEntityRow}.
 * Read-only; only getters verified against the 8.4 {@code identityiq.jar}. The parent Certification and the
 * Identity owner are preserved as reference ids/names. The latest {@code CertificationAction} decision is
 * flattened into scalar action_* fields, guarded against lazy-load faults. Nothing inferred.
 */
public final class NativeCertificationEntityMapper {

    private NativeCertificationEntityMapper() {
    }

    public static NativeCertificationEntityRow map(CertificationEntity e, String sourceSystem,
                                                   String extractionRunId) {
        NativeCertificationEntityRow row = new NativeCertificationEntityRow();

        row.setSourceId(e.getId());
        try {
            row.setCertificationId(e.getCertification() != null ? e.getCertification().getId() : null);
        } catch (Throwable t) {
            row.setCertificationId(null);
        }
        row.setIdentity(e.getIdentity());
        row.setApplication(e.getApplication());
        row.setNativeIdentity(e.getNativeIdentity());
        row.setAccountGroup(e.getAccountGroup());
        row.setFirstName(e.getFirstname());
        row.setLastName(e.getLastname());
        row.setFullName(e.getFullname());
        row.setReferenceAttribute(e.getReferenceAttribute());
        row.setSchemaObjectType(e.getSchemaObjectType());
        row.setSnapshotId(e.getSnapshotId());
        row.setPendingCertification(e.getPendingCertification());
        row.setType(enumName(e.getType()));
        row.setSummaryStatus(enumName(e.getSummaryStatus()));
        row.setEntityDelegated(Boolean.valueOf(e.isEntityDelegated()));
        row.setEntityDelegationStatus(enumName(e.getEntityDelegationStatus()));
        row.setCompositeScore(Integer.valueOf(e.getCompositeScore()));
        row.setTargetId(e.getTargetId());
        row.setTargetName(e.getTargetName());
        row.setTargetDisplayName(e.getTargetDisplayName());

        row.setCompleted(toInstant(e.getCompleted()));
        row.setCreated(toInstant(e.getCreated()));
        row.setModified(toInstant(e.getModified()));

        Identity owner = e.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        try {
            CertificationAction a = e.getAction();
            if (a != null) {
                row.setActionStatus(enumName(a.getStatus()));
                row.setActionDecisionDate(toInstant(a.getDecisionDate()));
                row.setActionRemediationAction(enumName(a.getRemediationAction()));
                row.setActionActorName(a.getActorName());
                row.setActionActorDisplayName(a.getActorDisplayName());
                row.setActionComments(a.getComments());
            }
        } catch (Throwable t) {
            // leave all action_* fields null on any lazy-load fault
        }

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
