package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeAssociationRef;
import com.keyforge.nativeiiq.model.NativeClassificationRef;
import com.keyforge.nativeiiq.model.NativeManagedAttributeExtractionResult;
import com.keyforge.nativeiiq.model.NativeManagedAttributeRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;
import com.keyforge.nativeiiq.model.NativeReferenceRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeManagedAttributeExtractionResult} into a plain JSON-friendly structure (only
 * Maps, Lists, Strings, Booleans, Numbers) so it serializes deterministically with IIQ's
 * {@code JsonHelper}. Pure Java (no SailPoint dependency) but bundled in the plugin. The field names
 * are the wire contract consumed by {@code com.keyforge.nativeload.NativeManagedAttributeFields}.
 */
public final class NativeManagedAttributeWire {

    private NativeManagedAttributeWire() {
    }

    /** Small always-serializable error envelope; contains no entitlement data. */
    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "ManagedAttribute");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    /** Response envelope: {@code {entity, sourceSystem, extractionRunId, start, limit, returned, rows}}. */
    public static Map<String, Object> envelope(NativeManagedAttributeExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeManagedAttributeRow r : result.getManagedAttributes()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "ManagedAttribute");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeManagedAttributeRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("value", r.getValue());
        m.put("displayName", r.getDisplayName());
        m.put("displayableName", r.getDisplayableName());
        m.put("attribute", r.getAttribute());
        m.put("type", r.getType());
        m.put("uuid", r.getUuid());
        m.put("referenceAttribute", r.getReferenceAttribute());
        m.put("purview", r.getPurview());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationName", r.getApplicationName());
        m.put("instance", r.getInstance());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("requestable", r.getRequestable());
        m.put("group", r.getGroup());
        m.put("permission", r.getPermission());
        m.put("uncorrelated", r.getUncorrelated());
        m.put("aggregated", r.getAggregated());
        m.put("iiqElevatedAccess", r.getIiqElevatedAccess());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("description", r.getDescription());
        m.put("descriptions", new LinkedHashMap<String, String>(r.getDescriptions()));

        m.put("permissions", permissions(r.getPermissions()));
        m.put("targetPermissions", permissions(r.getTargetPermissions()));

        List<Map<String, Object>> inh = new ArrayList<Map<String, Object>>();
        for (NativeReferenceRef ref : r.getInheritance()) {
            Map<String, Object> im = new LinkedHashMap<String, Object>();
            im.put("id", ref.getId());
            im.put("name", ref.getName());
            inh.add(im);
        }
        m.put("inheritance", inh);

        List<Map<String, Object>> assoc = new ArrayList<Map<String, Object>>();
        for (NativeAssociationRef a : r.getAssociations()) {
            Map<String, Object> am = new LinkedHashMap<String, Object>();
            am.put("targetName", a.getTargetName());
            am.put("targetType", a.getTargetType());
            am.put("ownerType", a.getOwnerType());
            am.put("ownerId", a.getOwnerId());
            am.put("applicationName", a.getApplicationName());
            am.put("objectId", a.getObjectId());
            am.put("rights", a.getRights());
            am.put("effective", Integer.valueOf(a.getEffective()));
            am.put("lastAggregation", a.getLastAggregation());
            am.put("hierarchy", a.getHierarchy());
            am.put("effectiveTargetName", a.getEffectiveTargetName());
            am.put("uniqueTargetName", a.getUniqueTargetName());
            am.put("inherited", Boolean.valueOf(a.isInherited()));
            am.put("flattened", Boolean.valueOf(a.isFlattened()));
            am.put("permission", Boolean.valueOf(a.isPermission()));
            am.put("account", Boolean.valueOf(a.isAccount()));
            am.put("attribute", Boolean.valueOf(a.isAttribute()));
            am.put("allowPermission", Boolean.valueOf(a.isAllowPermission()));
            am.put("denyPermission", Boolean.valueOf(a.isDenyPermission()));
            am.put("unstructured", Boolean.valueOf(a.isUnstructured()));
            am.put("iiqElevatedAccess", Boolean.valueOf(a.isIiqElevatedAccess()));
            assoc.add(am);
        }
        m.put("associations", assoc);

        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));

        List<Map<String, Object>> classifications = new ArrayList<Map<String, Object>>();
        for (NativeClassificationRef c : r.getClassifications()) {
            Map<String, Object> cm = new LinkedHashMap<String, Object>();
            cm.put("classificationName", c.getClassificationName());
            cm.put("classificationDisplayName", c.getClassificationDisplayName());
            cm.put("classificationType", c.getClassificationType());
            cm.put("classificationOrigin", c.getClassificationOrigin());
            cm.put("source", c.getSource());
            cm.put("ownerType", c.getOwnerType());
            cm.put("ownerId", c.getOwnerId());
            cm.put("effective", c.getEffective());
            classifications.add(cm);
        }
        m.put("classifications", classifications);
        m.put("classificationNames", new ArrayList<String>(r.getClassificationNames()));
        m.put("classificationDisplayNames", new ArrayList<String>(r.getClassificationDisplayNames()));

        m.put("sourceHash", r.getSourceHash());
        m.put("memberAttribute", r.getMemberAttribute());
        m.put("fullName", r.getFullName());
        m.put("groupType", r.getGroupType());
        m.put("inactive", r.getInactive());
        m.put("autoPromotion", r.getAutoPromotion());
        m.put("differencable", r.getDifferencable());

        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("lastRefresh", iso(r.getLastRefresh()));
        m.put("lastTargetAggregation", iso(r.getLastTargetAggregation()));

        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static List<Map<String, Object>> permissions(List<NativePermissionRef> perms) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (NativePermissionRef p : perms) {
            Map<String, Object> pm = new LinkedHashMap<String, Object>();
            pm.put("target", p.getTarget());
            pm.put("rights", p.getRights());
            pm.put("annotation", p.getAnnotation());
            pm.put("aggregationSource", p.getAggregationSource());
            out.add(pm);
        }
        return out;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
