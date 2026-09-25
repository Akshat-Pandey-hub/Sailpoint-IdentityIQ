package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeCertificationItemRow;
import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Maps a native {@code sailpoint.object.CertificationItem} into a {@link NativeCertificationItemRow},
 * folding its 1:1 {@code CertificationAction} (the decision) into {@code action_*} fields. Read-only; only
 * getters verified against the 8.4 {@code identityiq.jar}. Reference getters (certification, entity, action)
 * may lazy-load, so each is fetched defensively and degrades to {@code null} rather than failing the row.
 * No decision is inferred from item state — {@code action_*} stay null when there is no action.
 */
public final class NativeCertificationItemMapper {

    private NativeCertificationItemMapper() {
    }

    public static NativeCertificationItemRow map(CertificationItem c, String sourceSystem, String extractionRunId) {
        NativeCertificationItemRow row = new NativeCertificationItemRow();

        row.setSourceId(c.getId());
        row.setCertificationId(certId(c));
        row.setEntityId(entityId(c));
        row.setIdentity(c.getIdentity());
        row.setType(enumName(c.getType()));
        row.setSubType(enumName(c.getSubType()));
        row.setBundle(c.getBundle());
        row.setBundleAssignmentId(c.getBundleAssignmentId());
        row.setExceptionApplication(c.getExceptionApplication());
        row.setExceptionAttributeName(c.getExceptionAttributeName());
        row.setExceptionAttributeValue(c.getExceptionAttributeValue());
        row.setExceptionPermissionTarget(c.getExceptionPermissionTarget());
        row.setExceptionPermissionRight(c.getExceptionPermissionRight());
        row.setAccountGroup(c.getAccountGroup());
        row.setPhase(enumName(c.getPhase()));
        row.setSummaryStatus(enumName(c.getSummaryStatus()));
        row.setCompleted(toInstant(c.getCompleted()));
        row.setLastDecision(toInstant(c.getLastDecision()));
        row.setExpirationDate(toInstant(c.getExpirationDate()));
        row.setFinishedDate(toInstant(c.getFinishedDate()));
        row.setIiqElevatedAccess(c.getIiqElevatedAccess());
        row.setReviewed(Boolean.valueOf(c.isReviewed()));
        row.setDelegated(Boolean.valueOf(c.isDelegated()));
        row.setActedUpon(Boolean.valueOf(c.isActedUpon()));
        row.setHistorical(Boolean.valueOf(c.isHistorical()));
        row.setExpired(Boolean.valueOf(c.isExpired()));
        row.setTargetId(c.getTargetId());
        row.setTargetName(c.getTargetName());
        row.setShortDescription(c.getShortDescription());
        row.setViolationSummary(c.getViolationSummary());
        addAll(c.getApplicationNames(), row.getApplicationNames());
        addAll(c.getClassificationNames(), row.getClassificationNames());

        mapAction(c, row);

        Identity owner = c.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        // Additional native links (source-truth): the policy violation this item concerns (by id) and
        // the role assignment it refers to (IIQ XML). Read-only; null when the source has none.
        sailpoint.object.PolicyViolation pv = safePolicyViolation(c);
        row.setPolicyViolationId(pv == null ? null : pv.getId());
        row.setRoleAssignment(NativeSerialize.xml(safeRoleAssignment(c)));

        row.setCreated(toInstant(c.getCreated()));
        row.setModified(toInstant(c.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static sailpoint.object.PolicyViolation safePolicyViolation(CertificationItem c) {
        try {
            return c.getPolicyViolation();
        } catch (Throwable t) {
            return null;
        }
    }

    private static sailpoint.object.RoleAssignment safeRoleAssignment(CertificationItem c) {
        try {
            return c.getRoleAssignment();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void mapAction(CertificationItem c, NativeCertificationItemRow row) {
        try {
            CertificationAction a = c.getAction();
            if (a == null) {
                return;
            }
            row.setActionStatus(enumName(a.getStatus()));
            row.setActionDecisionDate(toInstant(a.getDecisionDate()));
            row.setActionDecisionCertificationId(a.getDecisionCertificationId());
            row.setActionRemediationAction(enumName(a.getRemediationAction()));
            row.setActionActorName(a.getActorName());
            row.setActionActorDisplayName(a.getActorDisplayName());
            row.setActionComments(a.getComments());
            row.setActionCompletionComments(a.getCompletionComments());
            row.setActionOwnerName(a.getOwnerName());
            row.setActionMitigationExpiration(toInstant(a.getMitigationExpiration()));
            row.setActionIsApproved(Boolean.valueOf(a.isApproved()));
            row.setActionIsRemediation(Boolean.valueOf(a.isRemediation()));
            row.setActionIsMitigation(Boolean.valueOf(a.isMitigation()));
            row.setActionIsDelegation(Boolean.valueOf(a.isDelegation()));
            row.setActionIsRevokeAccount(Boolean.valueOf(a.isRevokeAccount()));
            row.setActionIsAutoDecision(Boolean.valueOf(a.isAutoDecision()));
            row.setActionIsBulkCertified(Boolean.valueOf(a.isBulkCertified()));
        } catch (Throwable t) {
            // an unresolved action never fails the item
        }
    }

    private static String certId(CertificationItem c) {
        try {
            Certification cert = c.getCertification();
            return cert == null ? null : cert.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String entityId(CertificationItem c) {
        try {
            CertificationEntity e = c.getCertificationEntity();
            if (e == null) {
                e = c.getParent();
            }
            return e == null ? null : e.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void addAll(List<String> src, List<String> out) {
        if (src != null) {
            for (String s : src) {
                if (s != null) {
                    out.add(s);
                }
            }
        }
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
