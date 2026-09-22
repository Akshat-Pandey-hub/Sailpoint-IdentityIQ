package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code Identity}, produced by the native Java-API layer.
 * Same business entity as the REST {@code kf_identity}, but a SEPARATE model so native-only fields
 * (e.g. the manager reference, the full attribute map, richer role/account graphs) are preserved
 * rather than forced into the REST representation. Pure data holder (no SailPoint dependency) so the
 * mapper's output is unit-testable without a live IIQ.
 *
 * <p>Persistence is intentionally NOT wired here yet — this row is the well-defined extraction result
 * abstraction; the IIQ→our-DB transport is decided in a later phase.
 */
public final class NativeIdentityRow {

    // --- basic ---
    private String sourceId;
    private String name;
    private String displayName;
    private String displayableName;
    private String firstName;
    private String lastName;
    private String email;
    private Boolean inactive;
    private String type;
    private Boolean correlated;
    private Boolean managerStatus;

    // --- manager (native-only vs SCIM: the reference itself, not just an isManager flag) ---
    private String managerId;
    private String managerName;

    // --- administrator (native-only reference) ---
    private String administratorId;
    private String administratorName;

    // --- relationships ---
    private final List<NativeAccountRef> accounts = new ArrayList<NativeAccountRef>();
    private final List<String> assignedRoles = new ArrayList<String>();
    private final List<String> detectedRoles = new ArrayList<String>();
    private final List<NativeRoleAssignmentRef> roleAssignments = new ArrayList<NativeRoleAssignmentRef>();
    private final List<NativeRoleDetectionRef> roleDetections = new ArrayList<NativeRoleDetectionRef>();
    private final List<String> capabilities = new ArrayList<String>();
    private final List<String> controlledScopes = new ArrayList<String>();

    // --- full native attribute map (preserved losslessly) ---
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;
    private Instant lastRefresh;
    private Instant lastLogin;

    // --- lineage envelope (source identification; same concept as the REST path) ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Identity";
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

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }

    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }

    public Boolean getInactive() { return inactive; }
    public void setInactive(Boolean v) { this.inactive = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public Boolean getCorrelated() { return correlated; }
    public void setCorrelated(Boolean v) { this.correlated = v; }

    public Boolean getManagerStatus() { return managerStatus; }
    public void setManagerStatus(Boolean v) { this.managerStatus = v; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String v) { this.managerId = v; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String v) { this.managerName = v; }

    public String getAdministratorId() { return administratorId; }
    public void setAdministratorId(String v) { this.administratorId = v; }

    public String getAdministratorName() { return administratorName; }
    public void setAdministratorName(String v) { this.administratorName = v; }

    public List<NativeAccountRef> getAccounts() { return accounts; }
    public List<String> getAssignedRoles() { return assignedRoles; }
    public List<String> getDetectedRoles() { return detectedRoles; }
    public List<NativeRoleAssignmentRef> getRoleAssignments() { return roleAssignments; }
    public List<NativeRoleDetectionRef> getRoleDetections() { return roleDetections; }
    public List<String> getCapabilities() { return capabilities; }
    public List<String> getControlledScopes() { return controlledScopes; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public Instant getLastRefresh() { return lastRefresh; }
    public void setLastRefresh(Instant v) { this.lastRefresh = v; }

    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant v) { this.lastLogin = v; }

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
