package com.keyforge.iiq.parquet;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Id/time helpers for Parquet mapping. Lenient by design: an unparseable id yields {@code null}
 * (the raw {@code source_*} column always preserves the exact source id) so a single bad value never
 * fails an extraction. Canonicalisation matches the PostgreSQL path's convention (32-hex → dashed
 * UUID), so Parquet ids equal the DB ids for the same source object.
 */
public final class ParquetIds {

    private ParquetIds() {
    }

    /** 32-hex or dashed/braced GUID → canonical dashed UUID; {@code null} if blank/invalid. */
    public static String canonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            try {
                return UUID.fromString(dashed).toString();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Deterministic name-based UUID over the seed (for records the source gives no id). */
    public static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static Instant epochMillis(Long ms) {
        return ms == null ? null : Instant.ofEpochMilli(ms);
    }

    /** ISO-8601 instant ("…Z"/offset) → Instant, or null. Reuses the incremental parser. */
    public static Instant iso(String value) {
        return com.keyforge.iiq.incremental.SourceChangeTime.parseIso(value);
    }
}
