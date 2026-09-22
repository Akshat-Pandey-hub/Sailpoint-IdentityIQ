package com.keyforge.iiq.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Writes as-extracted HTTP response payloads into the immutable RAW zone, exactly as received.
 *
 * <p><b>Layout</b> (HLD §7.1): {@code <out>/<src_system>/<src_object_type>/<yyyy-MM-dd(UTC)>/<sha256>.json}.
 * <b>Content:</b> the verbatim response bytes — never parsed, reserialized, pretty-printed, reordered,
 * whitespace-normalized, or re-encoded. <b>Immutability:</b> content-addressed by SHA-256, written via
 * a temp file + atomic move, and <b>never overwritten or deleted</b>; if the target already exists the
 * payload is treated as already captured. If atomic move is unavailable, the archive fails safely
 * rather than replacing an existing payload. A per-run {@code _runs/<extraction_run_id>.jsonl} manifest
 * records provenance of every captured response.
 */
public final class RawArchive {

    public static final String SRC_SYSTEM = "IdentityIQ";

    private final RawConfig config;
    private final String runId;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object manifestLock = new Object();

    public RawArchive(RawConfig config, String runId) {
        this.config = config;
        this.runId = (runId == null || runId.isBlank()) ? "unknown-run" : runId;
    }

    public String runId() {
        return runId;
    }

    /**
     * Persists one response body verbatim and records a manifest line. Returns the {@code raw_ref}
     * (partition-relative path to the stored payload).
     *
     * @throws RawArchiveException if the payload cannot be written (fail-safe: never returns a
     *                             success it did not achieve)
     */
    public String capture(String srcObjectType, String srcInterface, String path, String body) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(bytes);
        String type = safeSegment(srcObjectType);
        String date = LocalDate.now(ZoneOffset.UTC).toString(); // yyyy-MM-dd
        Path dir = config.outputDir().resolve(SRC_SYSTEM).resolve(type).resolve(date);
        Path target = dir.resolve(sha + ".json");
        String rawRef = SRC_SYSTEM + "/" + type + "/" + date + "/" + sha + ".json";
        try {
            Files.createDirectories(dir);
            boolean written = writeOnce(dir, target, bytes);
            appendManifest(srcObjectType, srcInterface, path, sha, bytes.length, written);
            return rawRef;
        } catch (IOException e) {
            throw new RawArchiveException("Failed to persist RAW payload to " + target + ": " + e.getMessage(), e);
        }
    }

    /** Write-once: never overwrites an existing SHA-256 file. Returns true iff a new file was created. */
    private boolean writeOnce(Path dir, Path target, byte[] bytes) throws IOException {
        if (Files.exists(target)) {
            return false; // already captured — immutable, leave untouched
        }
        Path tmp = Files.createTempFile(dir, ".raw-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (FileAlreadyExistsException raced) {
                return false; // another writer created it first; never overwrite
            } catch (AtomicMoveNotSupportedException noAtomic) {
                // Fail safe: a plain move WITHOUT REPLACE_EXISTING (throws if the target exists),
                // so we never silently replace an existing payload.
                try {
                    Files.move(tmp, target);
                    return true;
                } catch (FileAlreadyExistsException raced) {
                    return false;
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void appendManifest(String srcObjectType, String srcInterface, String path,
                                String sha, int bytes, boolean written) throws IOException {
        ObjectNode line = mapper.createObjectNode();
        line.put("src_system", SRC_SYSTEM);
        line.put("src_object_type", srcObjectType);
        line.put("src_interface", srcInterface);
        line.put("path", path);
        line.put("sha256", sha);
        line.put("captured_at", Instant.now().toString());
        line.put("bytes", bytes);
        line.put("written", written);
        synchronized (manifestLock) {
            Files.createDirectories(config.runsDir());
            Path manifest = config.runsDir().resolve(runId + ".jsonl");
            Files.writeString(manifest, line.toString() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /** Hex SHA-256 of the exact payload bytes. */
    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Keeps object-type usable as a single path segment (dots preserved; separators/illegal chars → '_'). */
    static String safeSegment(String s) {
        if (s == null || s.isBlank()) {
            return "unknown";
        }
        return s.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
