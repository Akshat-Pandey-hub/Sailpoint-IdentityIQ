package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAccountEntitlementRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Permission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Expands a SailPoint {@code Link} into its account&nbsp;&harr;&nbsp;entitlement edges. Three edge
 * {@code type}s are produced, all carrying the same account context (link/identity/application/native
 * identity/instance):
 * <ul>
 *   <li>{@code ATTRIBUTE} — one per (attribute, value) from {@code Link.getEntitlementAttributes()}
 *       (schema attributes flagged as entitlements); multi-valued attributes flatten to one row per value.</li>
 *   <li>{@code PERMISSION} — one per {@code Permission} from {@code Link.getPermissions()}.</li>
 *   <li>{@code TARGET_PERMISSION} — one per {@code Permission} from {@code Link.getTargetPermissions()}.</li>
 * </ul>
 * Read-only, verified getters only, nothing inferred.
 */
public final class NativeAccountEntitlementMapper {

    static final String TYPE_ATTRIBUTE = "ATTRIBUTE";
    static final String TYPE_PERMISSION = "PERMISSION";
    static final String TYPE_TARGET_PERMISSION = "TARGET_PERMISSION";

    private NativeAccountEntitlementMapper() {
    }

    /** Appends every account-entitlement edge of {@code link} (attribute + permission + target-permission). */
    public static int mapInto(Link link, List<NativeAccountEntitlementRow> out,
                              String sourceSystem, String extractionRunId) {
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

        Attributes<String, Object> ents = link.getEntitlementAttributes();
        if (ents != null && !ents.isEmpty()) {
            for (Object keyObj : ents.keySet()) {
                if (keyObj == null) {
                    continue;
                }
                String attrName = keyObj.toString();
                Object value = ents.get(keyObj);
                if (value instanceof Collection<?>) {
                    for (Object v : (Collection<?>) value) {
                        out.add(attributeEdge(linkId, identityId, identityName, applicationId, applicationName,
                                nativeIdentity, instance, attrName, str(v), sourceSystem, extractionRunId));
                        count++;
                    }
                } else if (value instanceof Object[]) {
                    for (Object v : (Object[]) value) {
                        out.add(attributeEdge(linkId, identityId, identityName, applicationId, applicationName,
                                nativeIdentity, instance, attrName, str(v), sourceSystem, extractionRunId));
                        count++;
                    }
                } else if (value != null) {
                    out.add(attributeEdge(linkId, identityId, identityName, applicationId, applicationName,
                            nativeIdentity, instance, attrName, str(value), sourceSystem, extractionRunId));
                    count++;
                }
            }
        }

        count += mapPermissionsInto(out, link.getPermissions(), TYPE_PERMISSION,
                "sailpoint.object.Link.permissions", linkId, identityId, identityName, applicationId,
                applicationName, nativeIdentity, instance, sourceSystem, extractionRunId);
        count += mapPermissionsInto(out, link.getTargetPermissions(), TYPE_TARGET_PERMISSION,
                "sailpoint.object.Link.targetPermissions", linkId, identityId, identityName, applicationId,
                applicationName, nativeIdentity, instance, sourceSystem, extractionRunId);
        return count;
    }

    /**
     * Appends one edge per {@code Permission}; package-private so it can be unit-tested with hand-built
     * {@code Permission} objects (no live Link/schema needed). Returns the number appended.
     */
    static int mapPermissionsInto(List<NativeAccountEntitlementRow> out, List<Permission> permissions,
                                  String edgeType, String srcObjectType, String linkId, String identityId,
                                  String identityName, String applicationId, String applicationName,
                                  String nativeIdentity, String instance, String sourceSystem,
                                  String extractionRunId) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Permission p : permissions) {
            if (p == null) {
                continue;
            }
            NativeAccountEntitlementRow row = base(linkId, identityId, identityName, applicationId,
                    applicationName, nativeIdentity, instance, sourceSystem, extractionRunId);
            row.setType(edgeType);
            row.setSrcObjectType(srcObjectType);
            row.setPermissionTarget(p.getTarget());
            row.setPermissionRights(p.getRights());
            List<String> rights = p.getRightsList();
            if (rights != null) {
                for (String r : rights) {
                    if (r != null) {
                        row.getPermissionRightsList().add(r);
                    }
                }
            }
            row.setPermissionAnnotation(p.getAnnotation());
            row.setPermissionAggregationSource(p.getAggregationSource());
            Map<String, Object> attrs = p.getAttributes();
            if (attrs != null) {
                for (Map.Entry<String, Object> e : attrs.entrySet()) {
                    if (e.getKey() != null) {
                        row.getPermissionAttributes().put(e.getKey(), JsonSafe.toJsonSafe(e.getValue()));
                    }
                }
            }
            out.add(row);
            count++;
        }
        return count;
    }

    private static NativeAccountEntitlementRow attributeEdge(String linkId, String identityId, String identityName,
                                                             String applicationId, String applicationName,
                                                             String nativeIdentity, String instance, String attrName,
                                                             String value, String sourceSystem, String extractionRunId) {
        NativeAccountEntitlementRow row = base(linkId, identityId, identityName, applicationId, applicationName,
                nativeIdentity, instance, sourceSystem, extractionRunId);
        row.setType(TYPE_ATTRIBUTE);
        row.setAttributeName(attrName);
        row.setAttributeValue(value);
        return row;
    }

    private static NativeAccountEntitlementRow base(String linkId, String identityId, String identityName,
                                                    String applicationId, String applicationName,
                                                    String nativeIdentity, String instance,
                                                    String sourceSystem, String extractionRunId) {
        NativeAccountEntitlementRow row = new NativeAccountEntitlementRow();
        row.setLinkId(linkId);
        row.setIdentityId(identityId);
        row.setIdentityName(identityName);
        row.setApplicationId(applicationId);
        row.setApplicationName(applicationName);
        row.setNativeIdentity(nativeIdentity);
        row.setInstance(instance);
        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
