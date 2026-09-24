package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of one Policy constraint ({@code SODConstraint} / {@code GenericConstraint} /
 * {@code ActivityConstraint}), reached via its parent {@code Policy}. Explicit parent reference
 * ({@code policyId}) and, for SoD, explicit conflicting {@code Bundle} ids on each side. Pure data holder.
 */
public final class NativePolicyConstraintRow {

    private String sourceId;
    private String policyId;
    private String policyName;
    private String name;
    private String description;
    private String constraintType;
    private Integer weight;
    private String compensatingControl;
    private String violationOwnerId;
    private String violationOwnerName;
    private String violationOwnerType;
    private final List<Map<String, Object>> leftBundles = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> rightBundles = new ArrayList<Map<String, Object>>();
    private final List<Object> selectors = new ArrayList<Object>();
    private Integer selectorCount;
    private final Map<String, Object> arguments = new LinkedHashMap<String, Object>();
    private Instant created;
    private Instant modified;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.BaseConstraint";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String v) { this.policyId = v; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String v) { this.policyName = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getConstraintType() { return constraintType; }
    public void setConstraintType(String v) { this.constraintType = v; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer v) { this.weight = v; }
    public String getCompensatingControl() { return compensatingControl; }
    public void setCompensatingControl(String v) { this.compensatingControl = v; }
    public String getViolationOwnerId() { return violationOwnerId; }
    public void setViolationOwnerId(String v) { this.violationOwnerId = v; }
    public String getViolationOwnerName() { return violationOwnerName; }
    public void setViolationOwnerName(String v) { this.violationOwnerName = v; }
    public String getViolationOwnerType() { return violationOwnerType; }
    public void setViolationOwnerType(String v) { this.violationOwnerType = v; }
    public List<Map<String, Object>> getLeftBundles() { return leftBundles; }
    public List<Map<String, Object>> getRightBundles() { return rightBundles; }
    public List<Object> getSelectors() { return selectors; }
    public Integer getSelectorCount() { return selectorCount; }
    public void setSelectorCount(Integer v) { this.selectorCount = v; }
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
