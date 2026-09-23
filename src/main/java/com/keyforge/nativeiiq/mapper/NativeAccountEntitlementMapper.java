package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAccountEntitlementRow;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Link;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Expands a SailPoint {@code Link} into its account&nbsp;&harr;&nbsp;entitlement edges via
 * {@code Link.getEntitlementAttributes()} — the attributes IIQ's schema flags as entitlements, holding the
 * values currently aggregated on the account. One {@link NativeAccountEntitlementRow} per (link, attribute,
 * value); multi-valued attributes are flattened to one row per value. Read-only, verified getters only,
 * nothing inferred.
 */
public final class NativeAccountEntitlementMapper {

    private NativeAccountEntitlementMapper() {
    }

    /** Appends every account-entitlement edge of {@code link} to {@code out}. Returns the number appended. */
    public static int mapInto(Link link, List<NativeAccountEntitlementRow> out,
                              String sourceSystem, String extractionRunId) {
        Attributes<String, Object> ents = link.getEntitlementAttributes();
        if (ents == null || ents.isEmpty()) {
            return 0;
        }
        String linkId = link.getId();
        String applicationId = link.getApplicationId();
        String applicationName = link.getApplicationName();
        String nativeIdentity = link.getNativeIdentity();
        String instance = link.getInstance();
        String identityId = null;
        String identityName = null;
        Identity identity = link.getIdentity();
        if (identity != null) {
            identityId = identity.getId();
            identityName = identity.getName();
        }

        int count = 0;
        for (Object keyObj : ents.keySet()) {
            if (keyObj == null) {
                continue;
            }
            String attrName = keyObj.toString();
            Object value = ents.get(keyObj);
            if (value instanceof Collection<?>) {
                for (Object v : (Collection<?>) value) {
                    out.add(edge(linkId, identityId, identityName, applicationId, applicationName,
                            nativeIdentity, instance, attrName, str(v), sourceSystem, extractionRunId));
                    count++;
                }
            } else if (value instanceof Object[]) {
                for (Object v : (Object[]) value) {
                    out.add(edge(linkId, identityId, identityName, applicationId, applicationName,
                            nativeIdentity, instance, attrName, str(v), sourceSystem, extractionRunId));
                    count++;
                }
            } else if (value != null) {
                out.add(edge(linkId, identityId, identityName, applicationId, applicationName,
                        nativeIdentity, instance, attrName, str(value), sourceSystem, extractionRunId));
                count++;
            }
        }
        return count;
    }

    private static NativeAccountEntitlementRow edge(String linkId, String identityId, String identityName,
                                                    String applicationId, String applicationName,
                                                    String nativeIdentity, String instance, String attrName,
                                                    String value, String sourceSystem, String extractionRunId) {
        NativeAccountEntitlementRow row = new NativeAccountEntitlementRow();
        row.setLinkId(linkId);
        row.setIdentityId(identityId);
        row.setIdentityName(identityName);
        row.setApplicationId(applicationId);
        row.setApplicationName(applicationName);
        row.setNativeIdentity(nativeIdentity);
        row.setInstance(instance);
        row.setAttributeName(attrName);
        row.setAttributeValue(value);
        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
