package com.keyforge.iiq.raw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies immutable, verbatim, content-addressed RAW capture + the per-run provenance manifest. */
class RawArchiveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Deliberately irregular whitespace + field order to prove no reserialization/normalization.
    private static final String BODY = "{\"b\":1,   \"a\":  2,\n\"z\":\"CN=Devs\"}";

    private static RawArchive archive(Path dir, String runId) {
        return new RawArchive(new RawConfig(dir), runId);
    }

    @Test
    void sha256IsDeterministicAndContentAddressed() {
        byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
        assertEquals(RawArchive.sha256Hex(bytes), RawArchive.sha256Hex(bytes));
        assertNotEquals(RawArchive.sha256Hex(bytes), RawArchive.sha256Hex("different".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void samePayloadSameFilename_differentPayloadDifferentFilename(@TempDir Path dir) {
        String r1 = archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        String r2 = archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        String r3 = archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY + "X");
        assertEquals(r1, r2, "identical payload → identical raw_ref/filename");
        assertNotEquals(r1, r3, "different payload → different filename");
    }

    @Test
    void storesExactBytesWithoutReserialization(@TempDir Path dir) throws IOException {
        String rawRef = archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        Path stored = dir.resolve(rawRef);
        assertTrue(Files.exists(stored));
        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(stored),
                "stored bytes must equal the exact response body — no parse/reorder/whitespace change");
        assertEquals(BODY, Files.readString(stored, StandardCharsets.UTF_8));
    }

    @Test
    void partitionStructureIsSrcSystemTypeDate(@TempDir Path dir) {
        String rawRef = archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        assertTrue(rawRef.matches("IdentityIQ/sailpoint\\.object\\.AuditEvent/\\d{4}-\\d{2}-\\d{2}/[0-9a-f]{64}\\.json"),
                "raw_ref layout must be <src_system>/<type>/<yyyy-MM-dd>/<sha256>.json but was: " + rawRef);
    }

    @Test
    void reCaptureIsIdempotentAndNeverOverwrites(@TempDir Path dir) throws IOException {
        String rawRef = archive(dir, "run-1").capture("sailpoint.object.TaskResult", "scim", "p", BODY);
        Path stored = dir.resolve(rawRef);
        long firstModified = Files.getLastModifiedTime(stored).toMillis();
        byte[] first = Files.readAllBytes(stored);
        // capture the same payload again (even from a new archive/run)
        archive(dir, "run-2").capture("sailpoint.object.TaskResult", "scim", "p", BODY);
        assertArrayEquals(first, Files.readAllBytes(stored), "content must be unchanged");
        assertEquals(firstModified, Files.getLastModifiedTime(stored).toMillis(), "must not rewrite the file");
        // exactly one payload file in the partition (no duplicate)
        long jsonFiles;
        try (Stream<Path> s = Files.walk(dir)) {
            jsonFiles = s.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        }
        assertEquals(1, jsonFiles, "no duplicate RAW file for repeated identical payload");
    }

    @Test
    void noTempFilesLeftBehind(@TempDir Path dir) throws IOException {
        archive(dir, "run-1").capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        try (Stream<Path> s = Files.walk(dir)) {
            assertFalse(s.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no leftover temp files");
        }
    }

    @Test
    void manifestUsesRunIdAndCarriesProvenanceFields(@TempDir Path dir) throws IOException {
        archive(dir, "run-xyz").capture("sailpoint.object.ProvisioningTransaction", "classic-rest",
                "rest/provisioningTransactions", BODY);
        Path manifest = dir.resolve("_runs").resolve("run-xyz.jsonl");
        assertTrue(Files.exists(manifest), "manifest file name must be <extraction_run_id>.jsonl");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        JsonNode m = MAPPER.readTree(lines.get(0));
        assertEquals("IdentityIQ", m.get("src_system").asText());
        assertEquals("sailpoint.object.ProvisioningTransaction", m.get("src_object_type").asText());
        assertEquals("classic-rest", m.get("src_interface").asText());
        assertEquals("rest/provisioningTransactions", m.get("path").asText());
        assertEquals(64, m.get("sha256").asText().length());
        assertTrue(m.has("captured_at"));
        assertEquals(BODY.getBytes(StandardCharsets.UTF_8).length, m.get("bytes").asInt());
        assertTrue(m.get("written").asBoolean());
    }

    @Test
    void manifestRecordsDedupOnSecondIdenticalCapture(@TempDir Path dir) throws IOException {
        RawArchive a = archive(dir, "run-1");
        a.capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        a.capture("sailpoint.object.AuditEvent", "classic-ui", "p", BODY);
        List<String> lines = Files.readAllLines(dir.resolve("_runs").resolve("run-1.jsonl"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "both captures are recorded for provenance");
        assertTrue(MAPPER.readTree(lines.get(0)).get("written").asBoolean());
        assertFalse(MAPPER.readTree(lines.get(1)).get("written").asBoolean(), "second identical capture deduped");
    }
}
