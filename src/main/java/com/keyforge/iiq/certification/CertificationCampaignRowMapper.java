package com.keyforge.iiq.certification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps a {@link CertificationCampaign} to a {@link CertificationCampaignRow}. Pure and DB-free.
 * {@code campaignid} is the canonicalised IIQ id (raw id also preserved); {@code created} epoch
 * milliseconds are converted to a UTC {@link LocalDateTime}; all other fields pass through (blanked
 * to NULL only when genuinely empty). Nothing is invented.
 */
public final class CertificationCampaignRowMapper {

    private CertificationCampaignRowMapper() {
    }

    public static CertificationCampaignRow map(CertificationCampaign c) {
        return new CertificationCampaignRow(
                toCanonicalUuid(c.id()),
                blankToNull(c.id()),
                blankToNull(c.name()),
                blankToNull(c.ownerDisplayName()),
                blankToNull(c.status()),
                blankToNull(c.percentComplete()),
                epochMillisToUtc(c.created()),
                blankToNull(c.tagsJson()));
    }

    /** Converts source epoch milliseconds (UTC instant) to a UTC {@link LocalDateTime}, or null. */
    static LocalDateTime epochMillisToUtc(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new CertificationCampaignMappingException(
                    "IdentityIQ CertificationGroup id is missing; cannot form kf_certification_campaign.campaignid (UUID).");
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
            throw new CertificationCampaignMappingException(
                    "IdentityIQ CertificationGroup id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
