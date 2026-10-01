package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code accesshistory.HistoricalEntitlementCapture} — an immutable
 * historical record of one entitlement a HistoricalIdentity held at a point in time. Field-complete: every
 * meaningful data-bearing getter of the class and its {@code HistoricalCapture} parent is preserved, NULLs
 * included. Pure data holder; no inference, no name-based linking. The raw capture JSON, the built attribute
 * maps and the compression metadata are all retained.
 */
public final class NativeHistEntitlementCaptureRow {

    // identity of this capture row
    private String sourceId;
    private String name;
    // owning entity (the HistoricalIdentity) + the identity it captures
    private String entityId;
    private String entityName;
    private String identityId;
    private String identityName;
    private String identityEntitlementId;
    // entitlement coordinates
    private String applicationId;
    private String applicationName;
    private String nativeIdentity;
    private String instance;
    private String displayValue;
    private String attributeName;
    private String attributeValue;
    private String type;
    // provenance flags / references (source values, never inferred)
    private boolean grantedByRole;
    private String roleId;
    private String requestItemId;
    private String pendingRequestItemId;
    private String certificationItemId;
    private boolean deleted;
    // HistoricalCapture envelope metadata
    private Instant effectiveDate;
    private Instant extendedToDate;
    private boolean latest;
    private boolean compressed;
    private boolean brief;
    private boolean full;
    private boolean patch;
    private String smartHash;
    private String fullHash;
    private String jsonFormat;
    private String transformType;
    private String patchDocParent;
    private String compressedPropertyFlag;
    private String compressedPropertyFlagValue;
    private final List<String> compressiblePropertyNames = new ArrayList<String>();
    private String captureJson;
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    private final Map<String, Object> extendedAttributes = new LinkedHashMap<String, Object>();
    private Instant created;
    private Instant modified;

    // lineage
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.accesshistory.HistoricalEntitlementCapture";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String v) { this.entityId = v; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String v) { this.entityName = v; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getIdentityEntitlementId() { return identityEntitlementId; }
    public void setIdentityEntitlementId(String v) { this.identityEntitlementId = v; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }
    public String getDisplayValue() { return displayValue; }
    public void setDisplayValue(String v) { this.displayValue = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public boolean isGrantedByRole() { return grantedByRole; }
    public void setGrantedByRole(boolean v) { this.grantedByRole = v; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String v) { this.roleId = v; }
    public String getRequestItemId() { return requestItemId; }
    public void setRequestItemId(String v) { this.requestItemId = v; }
    public String getPendingRequestItemId() { return pendingRequestItemId; }
    public void setPendingRequestItemId(String v) { this.pendingRequestItemId = v; }
    public String getCertificationItemId() { return certificationItemId; }
    public void setCertificationItemId(String v) { this.certificationItemId = v; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Instant v) { this.effectiveDate = v; }
    public Instant getExtendedToDate() { return extendedToDate; }
    public void setExtendedToDate(Instant v) { this.extendedToDate = v; }
    public boolean isLatest() { return latest; }
    public void setLatest(boolean v) { this.latest = v; }
    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean v) { this.compressed = v; }
    public boolean isBrief() { return brief; }
    public void setBrief(boolean v) { this.brief = v; }
    public boolean isFull() { return full; }
    public void setFull(boolean v) { this.full = v; }
    public boolean isPatch() { return patch; }
    public void setPatch(boolean v) { this.patch = v; }
    public String getSmartHash() { return smartHash; }
    public void setSmartHash(String v) { this.smartHash = v; }
    public String getFullHash() { return fullHash; }
    public void setFullHash(String v) { this.fullHash = v; }
    public String getJsonFormat() { return jsonFormat; }
    public void setJsonFormat(String v) { this.jsonFormat = v; }
    public String getTransformType() { return transformType; }
    public void setTransformType(String v) { this.transformType = v; }
    public String getPatchDocParent() { return patchDocParent; }
    public void setPatchDocParent(String v) { this.patchDocParent = v; }
    public String getCompressedPropertyFlag() { return compressedPropertyFlag; }
    public void setCompressedPropertyFlag(String v) { this.compressedPropertyFlag = v; }
    public String getCompressedPropertyFlagValue() { return compressedPropertyFlagValue; }
    public void setCompressedPropertyFlagValue(String v) { this.compressedPropertyFlagValue = v; }
    public List<String> getCompressiblePropertyNames() { return compressiblePropertyNames; }
    public String getCaptureJson() { return captureJson; }
    public void setCaptureJson(String v) { this.captureJson = v; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Map<String, Object> getExtendedAttributes() { return extendedAttributes; }
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
