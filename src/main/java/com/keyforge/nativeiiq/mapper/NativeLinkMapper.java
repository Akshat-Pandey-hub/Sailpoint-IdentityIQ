package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeLinkRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.Schema;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a native {@code sailpoint.object.Link} (account) into a {@link NativeLinkRow}. Read-only: only
 * getters. Every relationship is null-guarded. Uses only getters verified against the real 8.4
 * {@code identityiq.jar}. Nothing is inferred — a native {@code null} stays {@code null}.
 *
 * <p><b>Credential safety.</b> Account attributes can carry secrets. This mapper uses the OFFICIAL
 * native mechanism: the Link's Application account {@code Schema} marks secret attributes with
 * {@code AttributeDefinition.getType() == BaseAttributeDefinition.TYPE_SECRET}; every such attribute is
 * redacted to {@code "<redacted>"} in both the attribute map and the entitlement-attribute map.
 * {@code getPasswordHistory()} (password hashes) is <b>never</b> mapped at all. Remaining values are
 * made JSON-safe while the object is still attached to its session (before the extractor decaches).
 */
public final class NativeLinkMapper {

    private static final String REDACTED = "<redacted>";

    private NativeLinkMapper() {
    }

    public static NativeLinkRow map(Link link, String sourceSystem, String extractionRunId) {
        NativeLinkRow row = new NativeLinkRow();

        row.setSourceId(link.getId());
        row.setUuid(link.getUuid());
        row.setNativeIdentity(link.getNativeIdentity());
        row.setDisplayName(link.getDisplayName());
        row.setDisplayableName(link.getDisplayableName());
        row.setInstance(link.getInstance());
        row.setComponentIds(link.getComponentIds());

        row.setApplicationId(link.getApplicationId());
        row.setApplicationName(link.getApplicationName());
        Identity identity = link.getIdentity();
        if (identity != null) {
            row.setIdentityId(identity.getId());
            row.setIdentityName(identity.getName());
        }

        row.setDisabled(Boolean.valueOf(link.isDisabled()));
        row.setLocked(Boolean.valueOf(link.isLocked()));
        row.setComposite(Boolean.valueOf(link.isComposite()));
        row.setManuallyCorrelated(Boolean.valueOf(link.isManuallyCorrelated()));
        row.setHasEntitlements(Boolean.valueOf(link.isEntitlements()));
        row.setIiqDisabled(link.getIiqDisabled());
        row.setIiqLocked(link.getIiqLocked());

        addPermissions(link.getPermissions(), row.getPermissions());
        addPermissions(link.getTargetPermissions(), row.getTargetPermissions());

        // Secret attribute names from the Application's account schema (official native mechanism).
        Set<String> secret = secretAttributeNames(link.getApplication());
        copyAttributes(link.getAttributes(), row.getAttributes(), secret);
        copyAttributes(link.getEntitlementAttributes(), row.getEntitlementAttributes(), secret);

        row.setCreated(toInstant(link.getCreated()));
        row.setModified(toInstant(link.getModified()));
        row.setLastRefresh(toInstant(link.getLastRefresh()));
        row.setLastTargetAggregation(toInstant(link.getLastTargetAggregation()));

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

    /** Copies attribute values into the row, redacting schema-declared secrets and JSON-safing the rest. */
    private static void copyAttributes(Attributes<String, Object> attrs, Map<String, Object> out, Set<String> secret) {
        if (attrs == null) {
            return;
        }
        for (Object key : attrs.keySet()) {
            if (key != null) {
                String k = key.toString();
                out.put(k, secret.contains(k) ? REDACTED : JsonSafe.toJsonSafe(attrs.get(key)));
            }
        }
    }

    /** Account-schema attribute names whose type is {@code TYPE_SECRET} — the values are never stored. */
    private static Set<String> secretAttributeNames(Application app) {
        Set<String> names = new HashSet<String>();
        if (app == null) {
            return names;
        }
        Schema schema = app.getAccountSchema();
        if (schema == null) {
            return names;
        }
        List<AttributeDefinition> defs = schema.getAttributes();
        if (defs != null) {
            for (AttributeDefinition def : defs) {
                if (def != null && BaseAttributeDefinition.TYPE_SECRET.equals(def.getType()) && def.getName() != null) {
                    names.add(def.getName());
                }
            }
        }
        return names;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
