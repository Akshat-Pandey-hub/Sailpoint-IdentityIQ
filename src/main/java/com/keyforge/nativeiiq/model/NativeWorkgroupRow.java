package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a governance <b>Workgroup</b>. IdentityIQ 8.4 has NO dedicated
 * {@code Workgroup} class — a workgroup is a {@code sailpoint.object.Identity} flagged
 * {@code isWorkgroup() == true}. This model captures that workgroup identity's own fields; workgroup
 * MEMBERSHIP (the N-N identity relationship) is a separate later stage and is NOT represented here.
 * Pure data holder (no SailPoint dependency).
 */
public final class NativeWorkgroupRow {

    // --- identity / core ---
    private String sourceId;
    private String name;
    private String displayName;
    private String displayableName;
    private String email;
    private String type;
    private String description;
    private String notificationOption;

    private Boolean inactive;
    /** Always true for extracted rows (the extractor filters {@code workgroup==true}); kept for clarity. */
    private Boolean workgroup;

    // --- owner (native reference) ---
    private String ownerId;
    private String ownerName;

    // --- capabilities the workgroup confers (property of the workgroup identity itself) ---
    private final List<String> capabilities = new ArrayList<String>();

    // --- extended attributes (JSON-safe) ---
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;

    // --- lineage envelope (source class is Identity — IIQ has no dedicated Workgroup class) ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Identity";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }

    public String getDisplayableName() { return displayableName; }
    public void setDisplayableName(String v) { this.displayableName = v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getNotificationOption() { return notificationOption; }
    public void setNotificationOption(String v) { this.notificationOption = v; }

    public Boolean getInactive() { return inactive; }
    public void setInactive(Boolean v) { this.inactive = v; }

    public Boolean getWorkgroup() { return workgroup; }
    public void setWorkgroup(Boolean v) { this.workgroup = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public List<String> getCapabilities() { return capabilities; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }

    public String getSrcInterface() { return srcInterface; }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }

    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }

    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
