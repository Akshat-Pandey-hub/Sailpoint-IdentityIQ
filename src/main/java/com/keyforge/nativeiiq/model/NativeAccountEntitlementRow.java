package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of one account&nbsp;&harr;&nbsp;entitlement edge, derived from a SailPoint
 * {@code Link}'s entitlement attributes ({@code Link.getEntitlementAttributes()}). This is the
 * <b>account-side aggregated truth</b>: a value currently present on the account for a schema attribute
 * flagged as an entitlement — distinct from {@code IdentityEntitlement} (IIQ's tracked/assigned record).
 * One row per (link, attribute name, value). Pure data holder.
 */
public final class NativeAccountEntitlementRow {

    private String linkId;
    private String identityId;
    private String identityName;
    private String applicationId;
    private String applicationName;
    private String nativeIdentity;
    private String instance;
    private String attributeName;
    private String attributeValue;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Link.entitlementAttributes";
    private String extractionRunId;
    private Instant extractedAt;

    public String getLinkId() { return linkId; }
    public void setLinkId(String v) { this.linkId = v; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }

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
