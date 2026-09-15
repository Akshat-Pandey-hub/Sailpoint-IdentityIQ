package com.keyforge.iiq.identityrole;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps an {@link IdentityRoleAssignment} to an {@link IdentityRoleRow}. Pure and DB-free.
 * {@code identityid}/{@code roleid} are canonicalised identically to the identity/role mappers so the
 * foreign keys line up; {@code id} is a deterministic name-based UUID over {@code (identityid|roleid)}
 * (an identity holds a given role at most once — verified: 0 duplicate pairs live). {@code date}
 * epoch-millis becomes a UTC {@link LocalDateTime}. Nothing is invented.
 */
public final class IdentityRoleRowMapper {

    private IdentityRoleRowMapper() {
    }

    public static IdentityRoleRow map(IdentityRoleAssignment a) {
        String identityid = toCanonicalUuid(a.identityId(), "Identity");
        String roleid = toCanonicalUuid(a.roleId(), "Role");
        String id = UUID.nameUUIDFromBytes((identityid + "|" + roleid).getBytes(StandardCharsets.UTF_8)).toString();
        return new IdentityRoleRow(
                id,
                identityid,
                roleid,
                blankToNull(a.identityId()),
                blankToNull(a.roleId()),
                blankToNull(a.roleDisplayName()),
                epochMillisToUtc(a.assignedDate()),
                blankToNull(a.assigner()),
                blankToNull(a.description()));
    }

    static LocalDateTime epochMillisToUtc(Long epochMillis) {
        return epochMillis == null ? null
                : Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    static String toCanonicalUuid(String rawId, String what) {
        if (rawId == null || rawId.isBlank()) {
            throw new IdentityRoleMappingException(
                    "IdentityIQ " + what + " id is missing; cannot form a kf_identity_role UUID key.");
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
            throw new IdentityRoleMappingException(
                    "IdentityIQ " + what + " id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
