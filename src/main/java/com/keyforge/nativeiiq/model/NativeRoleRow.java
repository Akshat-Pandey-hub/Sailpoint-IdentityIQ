package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code Bundle} (a role), produced by the native Java-API
 * layer. Same business entity as the REST {@code kf_role}, but a SEPARATE model preserving native-only
 * fields. Pure data holder (no SailPoint dependency).
 *
 * <p>Stage 5 scope is the role ENTITY only — role hierarchy (inheritance/permits/requirements),
 * profiles/entitlements and role-to-application relationships are deliberately NOT represented here;
 * they are separate upcoming stages.
 */
public final class NativeRoleRow {

    // --- identity / core ---
    private String sourceId;
    private String name;
    private String displayName;
    private String displayableName;
    private String fullName;
    private String description;
    private String type;
    private String assignmentId;

    // --- flags ---
    private Boolean activityEnabled;
    private Boolean allowDuplicateAccounts;
    private Boolean allowMultipleAssignments;
    private Boolean autoPromotion;
    private Boolean differencable;
    private Boolean iiqElevatedAccess;
    private Boolean mergeTemplates;
    private Boolean orProfiles;
    private Boolean pendingDelete;
    private Boolean hasSelector;

    private Integer riskScoreWeight;

    // --- owner (native reference) ---
    private String ownerId;
    private String ownerName;

    // --- lifecycle dates ---
    private Instant activationDate;
    private Instant deactivationDate;

    // --- descriptions + extended attributes (JSON-safe) ---
    private final Map<String, String> descriptions = new LinkedHashMap<String, String>();
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Bundle";
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

    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { this.assignmentId = v; }

    public Boolean getActivityEnabled() { return activityEnabled; }
    public void setActivityEnabled(Boolean v) { this.activityEnabled = v; }

    public Boolean getAllowDuplicateAccounts() { return allowDuplicateAccounts; }
    public void setAllowDuplicateAccounts(Boolean v) { this.allowDuplicateAccounts = v; }

    public Boolean getAllowMultipleAssignments() { return allowMultipleAssignments; }
    public void setAllowMultipleAssignments(Boolean v) { this.allowMultipleAssignments = v; }

    public Boolean getAutoPromotion() { return autoPromotion; }
    public void setAutoPromotion(Boolean v) { this.autoPromotion = v; }

    public Boolean getDifferencable() { return differencable; }
    public void setDifferencable(Boolean v) { this.differencable = v; }

    public Boolean getIiqElevatedAccess() { return iiqElevatedAccess; }
    public void setIiqElevatedAccess(Boolean v) { this.iiqElevatedAccess = v; }

    public Boolean getMergeTemplates() { return mergeTemplates; }
    public void setMergeTemplates(Boolean v) { this.mergeTemplates = v; }

    public Boolean getOrProfiles() { return orProfiles; }
    public void setOrProfiles(Boolean v) { this.orProfiles = v; }

    public Boolean getPendingDelete() { return pendingDelete; }
    public void setPendingDelete(Boolean v) { this.pendingDelete = v; }

    public Boolean getHasSelector() { return hasSelector; }
    public void setHasSelector(Boolean v) { this.hasSelector = v; }

    public Integer getRiskScoreWeight() { return riskScoreWeight; }
    public void setRiskScoreWeight(Integer v) { this.riskScoreWeight = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public Instant getActivationDate() { return activationDate; }
    public void setActivationDate(Instant v) { this.activationDate = v; }

    public Instant getDeactivationDate() { return deactivationDate; }
    public void setDeactivationDate(Instant v) { this.deactivationDate = v; }

    public Map<String, String> getDescriptions() { return descriptions; }
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
