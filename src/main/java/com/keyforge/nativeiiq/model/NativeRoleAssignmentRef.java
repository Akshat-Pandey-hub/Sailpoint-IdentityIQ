package com.keyforge.nativeiiq.model;

/**
 * A reference to one native {@code sailpoint.object.RoleAssignment} on an Identity — the assignment
 * side of the role graph (an explicitly granted role), richer than the flat assigned-role name list.
 * Pure data holder (no SailPoint dependency). Fields map to the getters verified in the 8.4 jar:
 * {@code getRoleName()}, {@code getRoleId()}, {@code getComments()}.
 */
public final class NativeRoleAssignmentRef {

    private final String roleName;
    private final String roleId;
    private final String comments;

    public NativeRoleAssignmentRef(String roleName, String roleId, String comments) {
        this.roleName = roleName;
        this.roleId = roleId;
        this.comments = comments;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getComments() {
        return comments;
    }
}
