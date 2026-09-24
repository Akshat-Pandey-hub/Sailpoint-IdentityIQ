package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deterministic PK + record-hash for native IdentityRequest persistence. */
class NativeIdentityRequestRepositoryTest {

    private static NativeIdentityRequestRecord rec(String sourceId, String state) {
        NativeIdentityRequestRecord r = new NativeIdentityRequestRecord();
        r.sourceId = sourceId;
        r.name = "0000123";
        r.state = state;
        return r;
    }

    @Test
    void requestIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeIdentityRequestRepository.canonicalRequestId(rec(hex, "executing"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void missingSourceIdFailsLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeIdentityRequestRepository.canonicalRequestId(rec(null, "executing")));
    }

    @Test
    void recordHashChangesWhenStateChanges() {
        String h1 = NativeIdentityRequestRepository.recordHash(rec("s1", "executing"));
        String h2 = NativeIdentityRequestRepository.recordHash(rec("s1", "terminated"));
        assertNotNull(h1);
        assertNotEquals(h1, h2);
    }
}
