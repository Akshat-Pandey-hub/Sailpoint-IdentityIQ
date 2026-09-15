package com.keyforge.iiq.identityrole;

import java.time.LocalDateTime;

/**
 * A persistable {@code kf_identity_role} row (a first-class Identity&rarr;Role assignment edge).
 * {@code id} is deterministic over (identity, role) so re-runs upsert idempotently. {@code identityid}
 * and {@code roleid} are canonical UUIDs matching {@code kf_identity.userid} and {@code kf_role.roleid};
 * the raw source ids are preserved. {@code assignedAt} is a UTC {@link LocalDateTime}.
 */
public record IdentityRoleRow(
        String id,
        String identityid,
        String roleid,
        String sourceIdentityId,
        String sourceRoleId,
        String roleDisplayName,
        LocalDateTime assignedAt,
        String assigner,
        String description) {
}
