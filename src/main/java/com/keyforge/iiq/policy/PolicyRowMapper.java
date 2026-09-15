package com.keyforge.iiq.policy;

import java.util.UUID;

/** Maps a {@link PolicyDefinition} to a {@link PolicyRow}. Pure and DB-free; nothing invented. */
public final class PolicyRowMapper {

    private PolicyRowMapper() {
    }

    public static PolicyRow map(PolicyDefinition p) {
        return new PolicyRow(
                toCanonicalUuid(p.id()),
                blankToNull(p.name()),
                blankToNull(p.type()),
                blankToNull(p.state()),
                blankToNull(p.description()));
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new PolicyMappingException(
                    "IdentityIQ policy id is missing; cannot use as kf_policy.policyid (UUID).");
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
            throw new PolicyMappingException(
                    "IdentityIQ policy id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
