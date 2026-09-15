package com.keyforge.iiq.workgroupmember;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Maps a {@link WorkgroupMembership} to a {@link WorkgroupMemberRow}. Pure and DB-free. Both
 * endpoints must resolve to real UUIDs (a membership without them is not a usable edge). The
 * primary key is deterministic on (workgroup_id, identity_id) so re-runs update in place and
 * the same identity appearing on multiple member pages never duplicates.
 */
public final class WorkgroupMemberRowMapper {

    private WorkgroupMemberRowMapper() {
    }

    public static WorkgroupMemberRow map(WorkgroupMembership m) {
        String workgroupId = toCanonicalUuid(m.workgroupId(), "workgroup");
        String identityId = toCanonicalUuid(m.identityId(), "member identity");
        String id = deterministicId(workgroupId, identityId);
        return new WorkgroupMemberRow(
                id, workgroupId, identityId,
                blankToNull(m.memberName()), blankToNull(m.firstName()), blankToNull(m.lastName()));
    }

    static String deterministicId(String workgroupId, String identityId) {
        String key = "WorkgroupMember|" + workgroupId + "|" + identityId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String toCanonicalUuid(String rawId, String what) {
        if (rawId == null || rawId.isBlank()) {
            throw new WorkgroupMemberMappingException(
                    "IdentityIQ " + what + " id is missing; cannot form a kf_workgroup_member edge.");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new WorkgroupMemberMappingException(
                    "IdentityIQ " + what + " id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
