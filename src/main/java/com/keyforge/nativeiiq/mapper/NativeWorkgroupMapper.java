package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeWorkgroupRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Identity;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Maps a native {@code sailpoint.object.Identity} that is a workgroup ({@code isWorkgroup()==true})
 * into a {@link NativeWorkgroupRow}. Read-only: only getters. Uses only getters verified against the
 * real 8.4 {@code identityiq.jar}. Nothing is inferred; a native {@code null} stays {@code null}.
 *
 * <p>Scope: the workgroup entity itself. Members (the inverse identity relationship) are a separate
 * stage and are NOT read here. {@code description} is surfaced from the identity's {@code description}
 * attribute (IIQ has no {@code Identity.getDescription()}). Extended attributes are JSON-safed while
 * the object is still attached to its session (before the extractor decaches).
 */
public final class NativeWorkgroupMapper {

    private NativeWorkgroupMapper() {
    }

    public static NativeWorkgroupRow map(Identity wg, String sourceSystem, String extractionRunId) {
        NativeWorkgroupRow row = new NativeWorkgroupRow();

        row.setSourceId(wg.getId());
        row.setName(wg.getName());
        row.setDisplayName(wg.getDisplayName());
        row.setDisplayableName(wg.getDisplayableName());
        row.setEmail(wg.getEmail());
        row.setType(wg.getType());
        row.setInactive(Boolean.valueOf(wg.isInactive()));
        row.setWorkgroup(Boolean.valueOf(wg.isWorkgroup()));

        Identity.WorkgroupNotificationOption opt = wg.getNotificationOption();
        if (opt != null) {
            row.setNotificationOption(opt.name());
        }

        Identity owner = wg.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        List<Capability> capabilities = wg.getCapabilities();
        if (capabilities != null) {
            for (Capability c : capabilities) {
                if (c != null) {
                    row.getCapabilities().add(c.getName());
                }
            }
        }

        Attributes<String, Object> attrs = wg.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    row.getAttributes().put(key.toString(), JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
            // Workgroup description is an identity attribute (no Identity.getDescription() exists).
            Object desc = attrs.get("description");
            if (desc != null) {
                row.setDescription(String.valueOf(desc));
            }
        }

        row.setCreated(toInstant(wg.getCreated()));
        row.setModified(toInstant(wg.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
