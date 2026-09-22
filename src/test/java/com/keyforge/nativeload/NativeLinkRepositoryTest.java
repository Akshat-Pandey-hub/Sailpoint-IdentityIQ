package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native Link/account persistence. */
class NativeLinkRepositoryTest {

    private static NativeLinkRecord record(String sourceId, String nativeIdentity) {
        NativeLinkRecord r = new NativeLinkRecord();
        r.sourceId = sourceId;
        r.nativeIdentity = nativeIdentity;
        r.applicationName = "AD";
        return r;
    }

    @Test
    void accountIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeLinkRepository.canonicalAccountId(record(hex, "CN=jsmith"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeLinkRepository.canonicalAccountId(record(null, "CN=jsmith"));
        String b = NativeLinkRepository.canonicalAccountId(record(null, "CN=jsmith"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithBusinessData() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeLinkRecord r1 = record(hex, "CN=jsmith");
        r1.disabled = Boolean.FALSE;
        NativeLinkRecord r2 = record(hex, "CN=jsmith");
        r2.disabled = Boolean.FALSE;
        NativeLinkRecord r3 = record(hex, "CN=jsmith");
        r3.disabled = Boolean.TRUE;
        assertNotNull(NativeLinkRepository.recordHash(r1));
        assertEquals(NativeLinkRepository.recordHash(r1), NativeLinkRepository.recordHash(r2));
        assertNotEquals(NativeLinkRepository.recordHash(r1), NativeLinkRepository.recordHash(r3));
    }
}
