package com.keyforge.nativeload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic SHA-256 (hex) over a record's business fields, sorted by key — the native-path
 * equivalent of the Parquet lineage {@code record_hash}. An unchanged source record hashes the same
 * across runs (basis for change detection). {@code null} values are normalized so presence/absence is
 * stable. Never throws (returns {@code null} if the digest is somehow unavailable).
 */
public final class NativeRecordHash {

    private NativeRecordHash() {
    }

    public static String of(Map<String, Object> businessFields) {
        TreeMap<String, Object> sorted = new TreeMap<>(businessFields);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue() == null ? " " : e.getValue()).append('\n');
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
