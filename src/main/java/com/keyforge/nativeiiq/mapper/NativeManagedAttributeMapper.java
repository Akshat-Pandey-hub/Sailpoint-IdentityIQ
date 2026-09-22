package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAssociationRef;
import com.keyforge.nativeiiq.model.NativeManagedAttributeRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;
import com.keyforge.nativeiiq.model.NativeReferenceRef;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.TargetAssociation;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.ManagedAttribute} into a {@link NativeManagedAttributeRow}.
 * Read-only: only getters. Every relationship is null-guarded. Uses only getters verified against the
 * real 8.4 {@code identityiq.jar} (confirmed at compile time under the native Maven profile). Nothing
 * is inferred — no approver, no approval workflow, no group/role membership beyond what the object
 * actually exposes; a native {@code null} stays {@code null}. Extended-attribute values are made
 * JSON-safe here, while the object is still attached to its session (before the extractor decaches).
 */
public final class NativeManagedAttributeMapper {

    private NativeManagedAttributeMapper() {
    }

    public static NativeManagedAttributeRow map(ManagedAttribute ma, String sourceSystem, String extractionRunId) {
        NativeManagedAttributeRow row = new NativeManagedAttributeRow();

        row.setSourceId(ma.getId());
        row.setName(ma.getName());
        row.setValue(ma.getValue());
        row.setDisplayName(ma.getDisplayName());
        row.setDisplayableName(ma.getDisplayableName());
        row.setAttribute(ma.getAttribute());
        row.setType(ma.getType());
        row.setUuid(ma.getUuid());
        row.setReferenceAttribute(ma.getReferenceAttribute());
        row.setPurview(ma.getPurview());

        // application reference: id directly, name via the (attached) Application object
        row.setApplicationId(ma.getApplicationId());
        Application app = ma.getApplication();
        if (app != null) {
            row.setApplicationName(app.getName());
        }
        row.setInstance(ma.getInstance());
        row.setNativeIdentity(ma.getNativeIdentity());

        row.setRequestable(Boolean.valueOf(ma.isRequestable()));
        row.setGroup(Boolean.valueOf(ma.isGroup()));
        row.setPermission(Boolean.valueOf(ma.isPermission()));
        row.setUncorrelated(Boolean.valueOf(ma.isUncorrelated()));
        row.setAggregated(Boolean.valueOf(ma.isAggregated()));
        row.setIiqElevatedAccess(Boolean.valueOf(ma.isIiqElevatedAccess()));

        // owner (native-only reference): the reference itself
        Identity owner = ma.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        // descriptions
        row.setDescription(ma.getDescription());
        Map<String, String> descriptions = ma.getDescriptions();
        if (descriptions != null) {
            for (Map.Entry<String, String> e : descriptions.entrySet()) {
                if (e.getKey() != null) {
                    row.getDescriptions().put(e.getKey(), e.getValue());
                }
            }
        }

        // permissions + target permissions
        addPermissions(ma.getPermissions(), row.getPermissions());
        addPermissions(ma.getTargetPermissions(), row.getTargetPermissions());

        // inheritance (list of parent ManagedAttributes)
        List<ManagedAttribute> inheritance = ma.getInheritance();
        if (inheritance != null) {
            for (ManagedAttribute parent : inheritance) {
                if (parent != null) {
                    row.getInheritance().add(new NativeReferenceRef(parent.getId(), parent.getName()));
                }
            }
        }

        // target associations (group/association linkage)
        List<TargetAssociation> associations = ma.getAssociations();
        if (associations != null) {
            for (TargetAssociation ta : associations) {
                if (ta != null) {
                    row.getAssociations().add(new NativeAssociationRef(
                            ta.getTargetName(), ta.getTargetType(), ta.getOwnerType(),
                            ta.getApplicationName(), ta.getObjectId()));
                }
            }
        }

        // full extended attribute map, made JSON-safe while still attached
        Attributes<String, Object> attrs = ma.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    row.getAttributes().put(key.toString(), JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        row.setCreated(toInstant(ma.getCreated()));
        row.setModified(toInstant(ma.getModified()));
        row.setLastRefresh(toInstant(ma.getLastRefresh()));
        row.setLastTargetAggregation(toInstant(ma.getLastTargetAggregation()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void addPermissions(List<Permission> perms, List<NativePermissionRef> out) {
        if (perms == null) {
            return;
        }
        for (Permission p : perms) {
            if (p != null) {
                out.add(new NativePermissionRef(p.getTarget(), p.getRights(), p.getAnnotation()));
            }
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
