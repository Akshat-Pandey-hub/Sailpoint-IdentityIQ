package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of one identity&nbsp;&harr;&nbsp;role edge, derived from an {@code Identity}'s
 * {@code getRoleAssignments()} (relationship_type=ASSIGNED) and {@code getRoleDetections()} (DETECTED).
 * Assigned edges carry the stable {@code assignmentId}; detected edges carry the detection's assignment-id
 * evidence + date. Pure data holder. No assigner/date is invented for RoleAssignment (not exposed by the API).
 */
public final class NativeIdentityRoleRow {

    private String identityId;
    private String identityName;
    private String roleId;
    private String roleName;
    private String relationshipType;   // ASSIGNED | DETECTED
    private String assignmentId;       // RoleAssignment.getId() (assigned)
    private String detectionAssignmentIds; // RoleDetection.getAssignmentIds() (detected)
    private String comments;           // RoleAssignment.getComments()
    private Boolean futureAssignment;  // RoleAssignment.isFutureAssignment()
    private Boolean promotedSoftPermit;// RoleAssignment.isPromotedSoftPermit()
    private Instant detectionDate;     // RoleDetection.getDate()
    private final List<Map<String, Object>> targets = new ArrayList<Map<String, Object>>();

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Identity.roles";
    private String extractionRunId;
    private Instant extractedAt;

    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String v) { this.roleId = v; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String v) { this.roleName = v; }
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String v) { this.relationshipType = v; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { this.assignmentId = v; }
    public String getDetectionAssignmentIds() { return detectionAssignmentIds; }
    public void setDetectionAssignmentIds(String v) { this.detectionAssignmentIds = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
    public Boolean getFutureAssignment() { return futureAssignment; }
    public void setFutureAssignment(Boolean v) { this.futureAssignment = v; }
    public Boolean getPromotedSoftPermit() { return promotedSoftPermit; }
    public void setPromotedSoftPermit(Boolean v) { this.promotedSoftPermit = v; }
    public Instant getDetectionDate() { return detectionDate; }
    public void setDetectionDate(Instant v) { this.detectionDate = v; }
    public List<Map<String, Object>> getTargets() { return targets; }

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
