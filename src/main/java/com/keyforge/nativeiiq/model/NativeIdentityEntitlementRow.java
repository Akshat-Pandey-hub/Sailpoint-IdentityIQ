package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native-source projection of a SailPoint {@code IdentityEntitlement} — IIQ's tracked
 * identity&nbsp;&harr;&nbsp;entitlement record, carrying assignment provenance. A single
 * {@code IdentityEntitlement} spans the identity, the account key it was seen on
 * ({@code nativeIdentity}/{@code instance}/{@code appName}), the entitlement attribute/value, and the
 * provenance (source, assigner, granted-by-role, source roles, aggregation state) plus reference ids to a
 * certification item / request item where IIQ populated them. Pure data holder (no SailPoint dependency).
 *
 * <p>This is <b>current-state relationship data</b> (not CEC): it participates in the standard native
 * soft-delete lifecycle. Nothing here is inferred — a native {@code null} stays {@code null}, and no
 * provenance classification is invented beyond what the object itself reports.
 */
public final class NativeIdentityEntitlementRow {

    // --- identity / account / entitlement anchors ---
    private String sourceId;          // IdentityEntitlement.getId()
    private String identityId;
    private String identityName;
    private String applicationId;     // getAppId()
    private String applicationName;   // getAppName()
    private String nativeIdentity;    // the account key
    private String instance;
    private String attributeName;     // getName()
    private String attributeValue;    // getStringValue()
    private final List<String> valueList = new ArrayList<String>();
    private String type;              // ManagedAttribute.Type name (Entitlement/Permission/TargetPermission)
    private String displayName;
    private String annotation;

    // --- provenance (only what the object exposes) ---
    private Boolean assigned;
    private Boolean grantedByRole;
    private Boolean allowed;
    private Boolean connected;
    private String aggregationState;  // Connected / Disconnected
    private String source;            // getSource() (string)
    private String sourceObject;      // getSourceObject() (Source enum name)
    private String assigner;
    private String assignmentId;
    private String assignmentNote;
    private String sourceAssignableRoles;
    private String sourceDetectedRoles;

    // --- reference linkage ids (no join implemented here; ids preserved for later stages) ---
    private String certificationItemId;
    private String pendingCertificationItemId;
    private String requestItemId;
    private String pendingRequestItemId;

    // --- timestamps ---
    private Instant startDate;
    private Instant endDate;
    private Instant created;
    private Instant modified;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.IdentityEntitlement";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
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
    public List<String> getValueList() { return valueList; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getAnnotation() { return annotation; }
    public void setAnnotation(String v) { this.annotation = v; }

    public Boolean getAssigned() { return assigned; }
    public void setAssigned(Boolean v) { this.assigned = v; }
    public Boolean getGrantedByRole() { return grantedByRole; }
    public void setGrantedByRole(Boolean v) { this.grantedByRole = v; }
    public Boolean getAllowed() { return allowed; }
    public void setAllowed(Boolean v) { this.allowed = v; }
    public Boolean getConnected() { return connected; }
    public void setConnected(Boolean v) { this.connected = v; }
    public String getAggregationState() { return aggregationState; }
    public void setAggregationState(String v) { this.aggregationState = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getSourceObject() { return sourceObject; }
    public void setSourceObject(String v) { this.sourceObject = v; }
    public String getAssigner() { return assigner; }
    public void setAssigner(String v) { this.assigner = v; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { this.assignmentId = v; }
    public String getAssignmentNote() { return assignmentNote; }
    public void setAssignmentNote(String v) { this.assignmentNote = v; }
    public String getSourceAssignableRoles() { return sourceAssignableRoles; }
    public void setSourceAssignableRoles(String v) { this.sourceAssignableRoles = v; }
    public String getSourceDetectedRoles() { return sourceDetectedRoles; }
    public void setSourceDetectedRoles(String v) { this.sourceDetectedRoles = v; }

    public String getCertificationItemId() { return certificationItemId; }
    public void setCertificationItemId(String v) { this.certificationItemId = v; }
    public String getPendingCertificationItemId() { return pendingCertificationItemId; }
    public void setPendingCertificationItemId(String v) { this.pendingCertificationItemId = v; }
    public String getRequestItemId() { return requestItemId; }
    public void setRequestItemId(String v) { this.requestItemId = v; }
    public String getPendingRequestItemId() { return pendingRequestItemId; }
    public void setPendingRequestItemId(String v) { this.pendingRequestItemId = v; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant v) { this.startDate = v; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant v) { this.endDate = v; }
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
