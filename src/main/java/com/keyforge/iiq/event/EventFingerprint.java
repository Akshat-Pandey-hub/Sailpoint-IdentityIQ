package com.keyforge.iiq.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Deterministic fingerprint and event-id generation for {@code kf_event}. Pure and DB-free.
 *
 * <p>The {@code event_fingerprint} is an md5 over the canonical, order-preserving join of the event's
 * business attributes (null → empty, trimmed — <b>not</b> lower-cased, so case-sensitive source values
 * such as DNs are never collapsed). The {@code event_id} is the name-based UUID of
 * {@code (src_system | src_object_type | src_object_id | event_fingerprint)} — exactly the PDF §7.3
 * dedup identity. A changed business attribute yields a different fingerprint, hence a different
 * {@code event_id}, hence a new appended event (never a mutation).
 */
public final class EventFingerprint {

    /** Logical source system for this phase (single-instance; instance-scoping deferred). */
    public static final String SRC_SYSTEM = "IdentityIQ";

    private EventFingerprint() {
    }

    /** Canonical md5 fingerprint over the given business-attribute parts, in order. */
    public static String fingerprint(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(norm(parts[i]));
        }
        return md5(sb.toString());
    }

    /** Deterministic event id = {@code nameUUID(src_system|src_object_type|src_object_id|fingerprint)}. */
    public static String eventId(String srcSystem, String srcObjectType, String srcObjectId, String fingerprint) {
        String key = norm(srcSystem) + "|" + norm(srcObjectType) + "|" + norm(srcObjectId) + "|" + norm(fingerprint);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
