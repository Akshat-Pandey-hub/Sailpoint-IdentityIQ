package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code ManagedAttribute} (an entitlement / group value),
 * produced by the native Java-API layer. Same business entity as the REST {@code kf_entitlement}, but
 * a SEPARATE model so native-only fields are preserved. Pure data holder (no SailPoint dependency) so
 * the mapper's output is unit-testable and no Hibernate object escapes the extraction boundary.
 */
public final class NativeManagedAttributeRow {

    // --- identity / core ---
    private String sourceId;
    private String name;
    private String value;
    private String displayName;
    private String displayableName;
    private String attribute;
    private String type;
    private String uuid;
    private String referenceAttribute;
    private String purview;

    // --- application reference ---
    private String applicationId;
    private String applicationName;
    private String instance;
    private String nativeIdentity;

    // --- flags ---
    private Boolean requestable;
    private Boolean group;
    private Boolean permission;
    private Boolean uncorrelated;
    private Boolean aggregated;
    private Boolean iiqElevatedAccess;

    // --- owner (native-only reference) ---
    private String ownerId;
    private String ownerName;

    // --- descriptions ---
    private String description;
    private final Map<String, String> descriptions = new LinkedHashMap<String, String>();

    // --- relationships ---
    private final List<NativePermissionRef> permissions = new ArrayList<NativePermissionRef>();
    private final List<NativePermissionRef> targetPermissions = new ArrayList<NativePermissionRef>();
    private final List<NativeReferenceRef> inheritance = new ArrayList<NativeReferenceRef>();
    private final List<NativeAssociationRef> associations = new ArrayList<NativeAssociationRef>();

    // --- extended attribute map (JSON-safe) ---
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;
    private Instant lastRefresh;
    private Instant lastTargetAggregation;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.ManagedAttribute";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }

    public String getDisplayableName() { return displayableName; }
    public void setDisplayableName(String v) { this.displayableName = v; }

    public String getAttribute() { return attribute; }
    public void setAttribute(String v) { this.attribute = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getUuid() { return uuid; }
    public void setUuid(String v) { this.uuid = v; }

    public String getReferenceAttribute() { return referenceAttribute; }
    public void setReferenceAttribute(String v) { this.referenceAttribute = v; }

    public String getPurview() { return purview; }
    public void setPurview(String v) { this.purview = v; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }

    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }

    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }

    public Boolean getRequestable() { return requestable; }
    public void setRequestable(Boolean v) { this.requestable = v; }

    public Boolean getGroup() { return group; }
    public void setGroup(Boolean v) { this.group = v; }

    public Boolean getPermission() { return permission; }
    public void setPermission(Boolean v) { this.permission = v; }

    public Boolean getUncorrelated() { return uncorrelated; }
    public void setUncorrelated(Boolean v) { this.uncorrelated = v; }

    public Boolean getAggregated() { return aggregated; }
    public void setAggregated(Boolean v) { this.aggregated = v; }

    public Boolean getIiqElevatedAccess() { return iiqElevatedAccess; }
    public void setIiqElevatedAccess(Boolean v) { this.iiqElevatedAccess = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public Map<String, String> getDescriptions() { return descriptions; }
    public List<NativePermissionRef> getPermissions() { return permissions; }
    public List<NativePermissionRef> getTargetPermissions() { return targetPermissions; }
    public List<NativeReferenceRef> getInheritance() { return inheritance; }
    public List<NativeAssociationRef> getAssociations() { return associations; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public Instant getLastRefresh() { return lastRefresh; }
    public void setLastRefresh(Instant v) { this.lastRefresh = v; }

    public Instant getLastTargetAggregation() { return lastTargetAggregation; }
    public void setLastTargetAggregation(Instant v) { this.lastTargetAggregation = v; }

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
