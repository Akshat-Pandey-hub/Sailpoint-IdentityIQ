package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * A reference to one native {@code sailpoint.object.RoleRequest} on an Identity — the authoritative
 * role grant/removal provenance record (who requested/assigned or removed which role, when, from what
 * source, with sunrise/sunset), distinct from the flat assigned-role list and from the
 * {@link NativeRoleAssignmentRef} (which carries only role name/id/comments). Pure data holder (no
 * SailPoint dependency). Fields map to getters verified in the 8.4 jar on {@code RoleRequest} and its
 * {@code RoleAssignment}/{@code Assignment} ancestors: {@code getRoleName()}, {@code getRoleId()},
 * {@code getAssigner()}, {@code getDate()}, {@code getSource()}, {@code isNegative()},
 * {@code getStartDate()}, {@code getEndDate()}, {@code getAssignmentId()}, {@code getComments()},
 * {@code getPermittedById()}, {@code getPermittedByName()}.
 */
public final class NativeRoleRequestRef {

    private final String roleName;
    private final String roleId;
    private final String assigner;
    private final Instant date;
    private final String source;
    private final Boolean negative;
    private final Instant startDate;
    private final Instant endDate;
    private final String assignmentId;
    private final String comments;
    private final String permittedById;
    private final String permittedByName;

    public NativeRoleRequestRef(String roleName, String roleId, String assigner, Instant date, String source,
                                Boolean negative, Instant startDate, Instant endDate, String assignmentId,
                                String comments, String permittedById, String permittedByName) {
        this.roleName = roleName;
        this.roleId = roleId;
        this.assigner = assigner;
        this.date = date;
        this.source = source;
        this.negative = negative;
        this.startDate = startDate;
        this.endDate = endDate;
        this.assignmentId = assignmentId;
        this.comments = comments;
        this.permittedById = permittedById;
        this.permittedByName = permittedByName;
    }

    public String getRoleName() { return roleName; }
    public String getRoleId() { return roleId; }
    public String getAssigner() { return assigner; }
    public Instant getDate() { return date; }
    public String getSource() { return source; }
    public Boolean getNegative() { return negative; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public String getAssignmentId() { return assignmentId; }
    public String getComments() { return comments; }
    public String getPermittedById() { return permittedById; }
    public String getPermittedByName() { return permittedByName; }
}
