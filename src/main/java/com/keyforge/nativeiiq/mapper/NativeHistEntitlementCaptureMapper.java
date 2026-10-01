package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeHistEntitlementCaptureRow;
import com.keyforge.nativeiiq.wire.JsonSafe;
import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.Attributes;
import sailpoint.object.accesshistory.HistoricalEntitlementCapture;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.accesshistory.HistoricalEntitlementCapture} into a
 * {@link NativeHistEntitlementCaptureRow}. Read-only; every getter is verified against the 8.4
 * {@code identityiq.jar} (the class plus its {@code HistoricalCapture} parent). Field-complete: values kept
 * verbatim, NULL preserved, enums as their source {@code name()}, the raw capture {@code json} kept as-is,
 * and the built attribute maps made JSON-safe. No inference, no name-based linking.
 */
public final class NativeHistEntitlementCaptureMapper {

    private NativeHistEntitlementCaptureMapper() {
    }

    public static NativeHistEntitlementCaptureRow map(HistoricalEntitlementCapture h,
                                                      String sourceSystem, String extractionRunId) {
        NativeHistEntitlementCaptureRow row = new NativeHistEntitlementCaptureRow();

        row.setSourceId(h.getId());
        row.setName(h.getName());
        row.setEntityId(h.getEntityId());
        row.setEntityName(h.getEntityName());
        row.setIdentityId(h.getIdentityId());
        row.setIdentityName(h.getIdentityName());
        row.setIdentityEntitlementId(h.getIdentityEntitlementId());
        row.setApplicationId(h.getApplicationId());
        row.setApplicationName(h.getApplicationName());
        row.setNativeIdentity(h.getNativeIdentity());
        row.setInstance(h.getInstance());
        row.setDisplayValue(h.getDisplayValue());
        row.setAttributeName(h.getAttributeName());
        row.setAttributeValue(h.getAttributeValue());
        row.setType(h.getType());
        row.setGrantedByRole(h.isGrantedByRole());
        row.setRoleId(h.getRoleId());
        row.setRequestItemId(h.getRequestItemId());
        row.setPendingRequestItemId(h.getPendingRequestItemId());
        row.setCertificationItemId(h.getCertificationItemId());
        row.setDeleted(h.isDeleted());
        row.setEffectiveDate(toInstant(h.getEffectiveDate()));
        row.setExtendedToDate(toInstant(h.getExtendedToDate()));
        row.setLatest(h.isLatest());
        row.setCompressed(h.isCompressed());
        row.setBrief(h.isBrief());
        row.setFull(h.isFull());
        row.setPatch(h.isPatch());
        row.setSmartHash(h.getSmartHash());
        row.setFullHash(h.getFullHash());
        row.setJsonFormat(NativeSerialize.enumName(h.getJsonFormat()));
        row.setTransformType(h.getTransformType());
        row.setPatchDocParent(h.getPatchDocParent());
        row.setCompressedPropertyFlag(h.getCompressedPropertyFlag());
        row.setCompressedPropertyFlagValue(h.getCompressedPropertyFlagValue());
        List<String> compressible = h.getCompressiblePropertyNames();
        if (compressible != null) {
            for (String p : compressible) {
                if (p != null) {
                    row.getCompressiblePropertyNames().add(p);
                }
            }
        }
        row.setCaptureJson(safeJson(h));
        copyAttributes(h.getAttributes(), row.getAttributes());
        copyGenericMap(h.getExtendedAttributes(), row.getExtendedAttributes());
        row.setCreated(toInstant(h.getCreated()));
        row.setModified(toInstant(h.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /** Raw stored capture payload, verbatim. Never throws — degrades to null. */
    private static String safeJson(HistoricalEntitlementCapture h) {
        try {
            return h.getJson();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void copyAttributes(Attributes<String, Object> attrs, Map<String, Object> out) {
        if (attrs == null) {
            return;
        }
        for (Object key : attrs.keySet()) {
            if (key != null) {
                String k = key.toString();
                out.put(k, JsonSafe.toJsonSafe(attrs.get(k)));
            }
        }
    }

    private static void copyGenericMap(Map<String, Object> attrs, Map<String, Object> out) {
        if (attrs == null) {
            return;
        }
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey(), JsonSafe.toJsonSafe(e.getValue()));
            }
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
