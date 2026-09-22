package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code Link} (an account), produced by the native Java-API
 * layer. Same business entity as the REST {@code kf_account}, but a SEPARATE model preserving
 * native-only fields. Pure data holder (no SailPoint dependency) so the mapper's output is
 * unit-testable and no Hibernate object escapes the extraction boundary.
 */
public final class NativeLinkRow {

    // --- identity / core ---
    private String sourceId;
    private String uuid;
    private String nativeIdentity;
    private String displayName;
    private String displayableName;
    private String instance;
    private String componentIds;

    // --- application + identity references ---
    private String applicationId;
    private String applicationName;
    private String identityId;
    private String identityName;

    // --- account state ---
    private Boolean disabled;
    private Boolean locked;
    private Boolean composite;
    private Boolean manuallyCorrelated;
    private Boolean hasEntitlements;
    private Boolean iiqDisabled;
    private Boolean iiqLocked;

    // --- permissions ---
    private final List<NativePermissionRef> permissions = new ArrayList<NativePermissionRef>();
    private final List<NativePermissionRef> targetPermissions = new ArrayList<NativePermissionRef>();

    // --- attribute maps (secrets redacted, JSON-safe) ---
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    private final Map<String, Object> entitlementAttributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;
    private Instant lastRefresh;
    private Instant lastTargetAggregation;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Link";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getUuid() { return uuid; }
    public void setUuid(String v) { this.uuid = v; }

    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }

    public String getDisplayableName() { return displayableName; }
    public void setDisplayableName(String v) { this.displayableName = v; }

    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }

    public String getComponentIds() { return componentIds; }
    public void setComponentIds(String v) { this.componentIds = v; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }

    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }

    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean v) { this.disabled = v; }

    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean v) { this.locked = v; }

    public Boolean getComposite() { return composite; }
    public void setComposite(Boolean v) { this.composite = v; }

    public Boolean getManuallyCorrelated() { return manuallyCorrelated; }
    public void setManuallyCorrelated(Boolean v) { this.manuallyCorrelated = v; }

    public Boolean getHasEntitlements() { return hasEntitlements; }
    public void setHasEntitlements(Boolean v) { this.hasEntitlements = v; }

    public Boolean getIiqDisabled() { return iiqDisabled; }
    public void setIiqDisabled(Boolean v) { this.iiqDisabled = v; }

    public Boolean getIiqLocked() { return iiqLocked; }
    public void setIiqLocked(Boolean v) { this.iiqLocked = v; }

    public List<NativePermissionRef> getPermissions() { return permissions; }
    public List<NativePermissionRef> getTargetPermissions() { return targetPermissions; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Map<String, Object> getEntitlementAttributes() { return entitlementAttributes; }

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
