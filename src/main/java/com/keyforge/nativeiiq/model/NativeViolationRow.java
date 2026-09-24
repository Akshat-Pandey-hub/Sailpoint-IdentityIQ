package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code PolicyViolation}. Explicit references only: the
 * violating identity, the policy, and the constraint are carried by their source ids/names. Pure data
 * holder (no SailPoint dependency).
 */
public final class NativeViolationRow {

    private String sourceId;
    private String name;
    private String identityId;
    private String identityName;
    private String policyId;
    private String policyName;
    private String constraintId;
    private String constraintName;
    private String status;
    private Boolean active;
    private String leftBundles;
    private String rightBundles;
    private String entitlementsMarkedForRemediation;
    private String bundlesMarkedForRemediation;
    private final List<Object> relevantApps = new ArrayList<Object>();
    private final List<Object> violatingEntitlements = new ArrayList<Object>();
    private final Map<String, Object> arguments = new LinkedHashMap<String, Object>();

    private Instant created;
    private Instant modified;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.PolicyViolation";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String v) { this.policyId = v; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String v) { this.policyName = v; }
    public String getConstraintId() { return constraintId; }
    public void setConstraintId(String v) { this.constraintId = v; }
    public String getConstraintName() { return constraintName; }
    public void setConstraintName(String v) { this.constraintName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public String getLeftBundles() { return leftBundles; }
    public void setLeftBundles(String v) { this.leftBundles = v; }
    public String getRightBundles() { return rightBundles; }
    public void setRightBundles(String v) { this.rightBundles = v; }
    public String getEntitlementsMarkedForRemediation() { return entitlementsMarkedForRemediation; }
    public void setEntitlementsMarkedForRemediation(String v) { this.entitlementsMarkedForRemediation = v; }
    public String getBundlesMarkedForRemediation() { return bundlesMarkedForRemediation; }
    public void setBundlesMarkedForRemediation(String v) { this.bundlesMarkedForRemediation = v; }
    public List<Object> getRelevantApps() { return relevantApps; }
    public List<Object> getViolatingEntitlements() { return violatingEntitlements; }
    public Map<String, Object> getArguments() { return arguments; }
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
