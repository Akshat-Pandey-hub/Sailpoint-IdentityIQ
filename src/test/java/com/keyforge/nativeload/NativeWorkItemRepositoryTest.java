package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deterministic PK + record-hash for native WorkItem persistence. */
class NativeWorkItemRepositoryTest {

    private static NativeWorkItemRecord rec(String sourceId, String state) {
        NativeWorkItemRecord r = new NativeWorkItemRecord();
        r.sourceId = sourceId;
        r.name = "Approval 0000123";
        r.type = "Approval";
        r.state = state;
        r.identityRequestId = "0000123";
        return r;
    }

    @Test
    void workItemIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeWorkItemRepository.canonicalWorkItemId(rec(hex, "Pending"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void missingSourceIdFailsLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeWorkItemRepository.canonicalWorkItemId(rec(null, "Pending")));
    }

    @Test
    void recordHashChangesWhenStateChanges() {
        String h1 = NativeWorkItemRepository.recordHash(rec("s1", "Pending"));
        String h2 = NativeWorkItemRepository.recordHash(rec("s1", "Finished"));
        assertNotNull(h1);
        assertNotEquals(h1, h2);
    }
}
