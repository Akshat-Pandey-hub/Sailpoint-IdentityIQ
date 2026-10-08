package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of one identity&nbsp;&harr;&nbsp;role edge, derived from an {@code Identity}'s
 * {@code getRoleAssignments()} (relationship_type=ASSIGNED) and {@code getRoleDetections()} (DETECTED).
 * Assigned edges carry the stable {@code assignmentId} plus the assignment provenance exposed by
 * {@code RoleAssignment}'s parent {@code sailpoint.object.Assignment} ({@code getAssigner}/{@code getDate}/
 * {@code getStartDate}/{@code getEndDate}/{@code getSource}/{@code isNegative}/{@code isManual}); detected
 * edges carry the detection's assignment-id evidence + date (and null assignment-provenance). Pure data
 * holder. Nothing is invented — every value is read verbatim from the object or left {@code null}.
 */
public final class NativeIdentityRoleRow {

    private String identityId;
    private String identityName;
    private String roleId;
    private String roleName;
    private String relationshipType;   // ASSIGNED | DETECTED
    private String assignmentId;       // RoleAssignment.getId() (assigned)
    private String assigner;           // Assignment.getAssigner() (assigned)
    private Instant assignedDate;      // Assignment.getDate() (assigned)
    private Instant startDate;         // Assignment.getStartDate() (assigned, sunrise)
    private Instant endDate;           // Assignment.getEndDate() (assigned, sunset)
    private String source;             // Assignment.getSource() (assigned)
    private Boolean negative;          // Assignment.isNegative() (assigned)
    private Boolean manual;            // Assignment.isManual() (assigned)
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
    public String getAssigner() { return assigner; }
    public void setAssigner(String v) { this.assigner = v; }
    public Instant getAssignedDate() { return assignedDate; }
    public void setAssignedDate(Instant v) { this.assignedDate = v; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant v) { this.startDate = v; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant v) { this.endDate = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public Boolean getNegative() { return negative; }
    public void setNegative(Boolean v) { this.negative = v; }
    public Boolean getManual() { return manual; }
    public void setManual(Boolean v) { this.manual = v; }
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
