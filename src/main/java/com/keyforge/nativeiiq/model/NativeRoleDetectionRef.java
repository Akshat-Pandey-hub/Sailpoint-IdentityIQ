package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * A reference to one native {@code sailpoint.object.RoleDetection} on an Identity — the detection
 * side of the role graph (a role inferred from entitlements the account already holds). Pure data
 * holder (no SailPoint dependency). Fields map to the getters verified in the 8.4 jar:
 * {@code getRoleName()}, {@code getRoleId()}, {@code getDate()}, {@code getAssignmentIds()}.
 */
public final class NativeRoleDetectionRef {

    private final String roleName;
    private final String roleId;
    private final Instant date;
    private final String assignmentIds;

    public NativeRoleDetectionRef(String roleName, String roleId, Instant date, String assignmentIds) {
        this.roleName = roleName;
        this.roleId = roleId;
        this.date = date;
        this.assignmentIds = assignmentIds;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleId() {
        return roleId;
    }

    public Instant getDate() {
        return date;
    }

    public String getAssignmentIds() {
        return assignmentIds;
    }
}
