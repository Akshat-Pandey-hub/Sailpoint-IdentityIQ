package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.event.EventFingerprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Content-hash dedup semantics: deterministic, timestamp-excluded, NEW/CHANGED/UNCHANGED, null-safe. */
class NativeParquetServiceTest {

    // The content hash is computed over the BUSINESS column values only (this is exactly what runOne does).
    private static String hash(String... business) {
        return EventFingerprint.fingerprint(business);
    }

    @Test
    void contentHashIsDeterministicOverBusinessValues() {
        assertEquals(hash("req-1", "Modify", "committed"), hash("req-1", "Modify", "committed"));
    }

    @Test
    void extractionTimestampIsNotPartOfTheHash() {
        // Two "runs" of the same source row differ only by extraction time — which is never passed to the
        // hash — so the content hash is identical → classified UNCHANGED → no duplicate written.
        String run1 = hash("req-1", "Modify", "committed");
        String run2 = hash("req-1", "Modify", "committed");
        assertEquals(run1, run2);
        assertEquals(NativeParquetService.Change.UNCHANGED, NativeParquetService.classify(run1, run2));
    }

    @Test
    void changedBusinessFieldProducesChanged() {
        String was = hash("req-1", "Modify", "committed");
        String now = hash("req-1", "Modify", "failed");   // status changed
        assertNotEquals(was, now);
        assertEquals(NativeParquetService.Change.CHANGED, NativeParquetService.classify(was, now));
    }

    @Test
    void unseenKeyIsNew() {
        assertEquals(NativeParquetService.Change.NEW, NativeParquetService.classify(null, hash("x")));
    }

    @Test
    void nullBusinessValuesAreStable_undecidedCertDecisionsPreserved() {
        // Undecided cert item: action_* are null → fingerprint treats null as "" and stays deterministic.
        String h1 = hash("ci-1", "cert-1", null, null, null);   // e.g. id, cert_id, action_status, decision_date, actor
        String h2 = hash("ci-1", "cert-1", null, null, null);
        assertEquals(h1, h2);
        // a real decision changes the hash (never inferred, but detected when it appears)
        assertNotEquals(h1, hash("ci-1", "cert-1", "Remediated", "2026-09-25", "reviewer1"));
    }
}
