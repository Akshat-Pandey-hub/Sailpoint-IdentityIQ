package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native CertificationEntity persistence. */
class NativeCertificationEntityRepositoryTest {

    private static NativeCertificationEntityRecord rec(String sourceId, String certificationId) {
        NativeCertificationEntityRecord r = new NativeCertificationEntityRecord();
        r.sourceId = sourceId;
        r.certificationId = certificationId;
        r.identity = "alice";
        return r;
    }

    @Test
    void entityIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeCertificationEntityRepository.canonicalCertificationEntityId(rec(hex, "c1"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void recordHashChangesWhenCertificationChanges() {
        String h1 = NativeCertificationEntityRepository.recordHash(rec("s1", "c1"));
        String h2 = NativeCertificationEntityRepository.recordHash(rec("s1", "c2"));
        assertNotNull(h1);
        assertNotEquals(h1, h2);
    }
}
