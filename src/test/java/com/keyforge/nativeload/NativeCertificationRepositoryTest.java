package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native Certification persistence. */
class NativeCertificationRepositoryTest {

    private static NativeCertificationRecord rec(String sourceId, String phase) {
        NativeCertificationRecord r = new NativeCertificationRecord();
        r.sourceId = sourceId;
        r.name = "Q1 Manager Cert";
        r.phase = phase;
        return r;
    }

    @Test
    void certificationIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeCertificationRepository.canonicalCertificationId(rec(hex, "Active"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void recordHashChangesWhenPhaseChanges() {
        String h1 = NativeCertificationRepository.recordHash(rec("s1", "Active"));
        String h2 = NativeCertificationRepository.recordHash(rec("s1", "Remediation"));
        assertNotNull(h1);
        assertNotEquals(h1, h2);
    }
}
