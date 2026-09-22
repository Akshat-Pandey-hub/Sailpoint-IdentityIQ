package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the native Identity primary key is deterministic (never random) and joins to the REST path.
 */
class NativeIdentityRepositoryTest {

    private static NativeIdentityRecord record(String sourceId, String name) {
        NativeIdentityRecord r = new NativeIdentityRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void useridIsCanonicalUuidOfSourceIdMatchingRestPath() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String userid = NativeIdentityRepository.canonicalUserid(record(hex, "jsmith"));
        // Same derivation the REST kf_identity uses → the two schemas join on this key.
        assertEquals(ParquetIds.canonicalUuid(hex), userid);
        assertEquals(userid, UUID.fromString(userid).toString(), "must be a valid canonical UUID");
    }

    @Test
    void useridIsStableAcrossCalls() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        assertEquals(NativeIdentityRepository.canonicalUserid(record(hex, "a")),
                NativeIdentityRepository.canonicalUserid(record(hex, "b")),
                "userid derives from the source id, not the name");
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String u1 = NativeIdentityRepository.canonicalUserid(record(null, "orphan"));
        String u2 = NativeIdentityRepository.canonicalUserid(record(null, "orphan"));
        assertNotNull(u1);
        assertEquals(u1, u2, "fallback must be deterministic, never random");
        assertEquals(u1, UUID.fromString(u1).toString(), "fallback is a valid UUID");
    }
}
