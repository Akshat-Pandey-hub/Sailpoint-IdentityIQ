package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native PolicyViolation persistence. */
class NativeViolationRepositoryTest {

    private static NativeViolationRecord rec(String sourceId, String status) {
        NativeViolationRecord r = new NativeViolationRecord();
        r.sourceId = sourceId;
        r.name = "SoD-1";
        r.policyId = "pol-1";
        r.identityId = "id-1";
        r.status = status;
        return r;
    }

    @Test
    void violationIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeViolationRepository.canonicalViolationId(rec(hex, "Open"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeViolationRepository.canonicalViolationId(rec(null, "Open"));
        String b = NativeViolationRepository.canonicalViolationId(rec(null, "Open"));
        assertNotNull(a);
        assertEquals(a, b);
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashChangesWithStatus() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        assertEquals(NativeViolationRepository.recordHash(rec(hex, "Open")),
                NativeViolationRepository.recordHash(rec(hex, "Open")));
        assertNotEquals(NativeViolationRepository.recordHash(rec(hex, "Open")),
                NativeViolationRepository.recordHash(rec(hex, "Remediated")));
    }
}
