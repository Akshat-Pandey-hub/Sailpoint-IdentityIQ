package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeIdentityEntitlementRow;

import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequestItem;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Maps a native {@code sailpoint.object.IdentityEntitlement} into a {@link NativeIdentityEntitlementRow}.
 * Read-only: only getters verified against the real 8.4 {@code identityiq.jar} (its own getters plus those
 * inherited from {@code PersistentIdentityItem}). Nothing is inferred — a native {@code null} stays
 * {@code null}, and provenance flags/values are taken verbatim from the object (no "direct/birthright/
 * requested" classification is invented).
 *
 * <p>Reference getters ({@code getCertificationItem}, {@code getRequestItem}, and their pending variants)
 * may trigger a lazy load; each is fetched defensively so a single unresolved reference degrades to a
 * {@code null} id rather than failing the row.
 */
public final class NativeIdentityEntitlementMapper {

    private NativeIdentityEntitlementMapper() {
    }

    public static NativeIdentityEntitlementRow map(IdentityEntitlement e, String sourceSystem, String extractionRunId) {
        NativeIdentityEntitlementRow row = new NativeIdentityEntitlementRow();

        row.setSourceId(e.getId());

        Identity identity = e.getIdentity();
        if (identity != null) {
            row.setIdentityId(identity.getId());
            row.setIdentityName(identity.getName());
        }

        row.setApplicationId(e.getAppId());
        row.setApplicationName(e.getAppName());
        row.setNativeIdentity(e.getNativeIdentity());
        row.setInstance(e.getInstance());
        row.setAttributeName(e.getName());
        row.setAttributeValue(e.getStringValue());
        List<String> values = e.getValueList();
        if (values != null) {
            for (String v : values) {
                if (v != null) {
                    row.getValueList().add(v);
                }
            }
        }
        row.setType(enumName(e.getType()));
        row.setDisplayName(e.getDisplayName());
        row.setAnnotation(e.getAnnotation());

        row.setAssigned(Boolean.valueOf(e.isAssigned()));
        row.setGrantedByRole(Boolean.valueOf(e.isGrantedByRole()));
        row.setAllowed(Boolean.valueOf(e.isAllowed()));
        row.setConnected(Boolean.valueOf(e.isConnected()));
        row.setAggregationState(enumName(e.getAggregationState()));
        row.setSource(e.getSource());
        row.setSourceObject(enumName(e.getSourceObject()));
        row.setAssigner(e.getAssigner());
        row.setAssignmentId(e.getAssignmentId());
        row.setAssignmentNote(e.getAssignmentNote());
        row.setSourceAssignableRoles(e.getSourceAssignableRoles());
        row.setSourceDetectedRoles(e.getSourceDetectedRoles());

        row.setCertificationItemId(certId(e));
        row.setPendingCertificationItemId(pendingCertId(e));
        row.setRequestItemId(requestId(e));
        row.setPendingRequestItemId(pendingRequestId(e));

        row.setStartDate(toInstant(e.getStartDate()));
        row.setEndDate(toInstant(e.getEndDate()));
        row.setCreated(toInstant(e.getCreated()));
        row.setModified(toInstant(e.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static String certId(IdentityEntitlement e) {
        try {
            CertificationItem ci = e.getCertificationItem();
            return ci == null ? null : ci.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String pendingCertId(IdentityEntitlement e) {
        try {
            CertificationItem ci = e.getPendingCertificationItem();
            return ci == null ? null : ci.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String requestId(IdentityEntitlement e) {
        try {
            IdentityRequestItem ri = e.getRequestItem();
            return ri == null ? null : ri.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String pendingRequestId(IdentityEntitlement e) {
        try {
            IdentityRequestItem ri = e.getPendingRequestItem();
            return ri == null ? null : ri.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
