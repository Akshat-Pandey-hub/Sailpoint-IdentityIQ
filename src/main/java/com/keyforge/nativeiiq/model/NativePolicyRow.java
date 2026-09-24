package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native-source projection of a SailPoint {@code Policy} (the definition). Pure data holder. */
public final class NativePolicyRow {

    private String sourceId;
    private String name;
    private String type;
    private String typeKey;
    private String description;
    private final Map<String, String> descriptions = new LinkedHashMap<String, String>();
    private String executor;
    private String violationOwnerId;
    private String violationOwnerName;
    private Integer constraintCount;
    private Instant created;
    private Instant modified;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Policy";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String v) { this.typeKey = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Map<String, String> getDescriptions() { return descriptions; }
    public String getExecutor() { return executor; }
    public void setExecutor(String v) { this.executor = v; }
    public String getViolationOwnerId() { return violationOwnerId; }
    public void setViolationOwnerId(String v) { this.violationOwnerId = v; }
    public String getViolationOwnerName() { return violationOwnerName; }
    public void setViolationOwnerName(String v) { this.violationOwnerName = v; }
    public Integer getConstraintCount() { return constraintCount; }
    public void setConstraintCount(Integer v) { this.constraintCount = v; }
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
