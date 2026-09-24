package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native TaskResult persistence. */
class NativeTaskResultRepositoryTest {

    private static NativeTaskResultRecord record(String sourceId, String name) {
        NativeTaskResultRecord r = new NativeTaskResultRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void taskResultIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeTaskResultRepository.canonicalTaskResultId(record(hex, "Account Aggregation"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeTaskResultRepository.canonicalTaskResultId(record(null, "Account Aggregation"));
        String b = NativeTaskResultRepository.canonicalTaskResultId(record(null, "Account Aggregation"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithCompletionStatus() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeTaskResultRecord r1 = record(hex, "Account Aggregation");
        r1.completionStatus = "Success";
        NativeTaskResultRecord r2 = record(hex, "Account Aggregation");
        r2.completionStatus = "Success";
        NativeTaskResultRecord r3 = record(hex, "Account Aggregation");
        r3.completionStatus = "Error";
        assertNotNull(NativeTaskResultRepository.recordHash(r1));
        assertEquals(NativeTaskResultRepository.recordHash(r1), NativeTaskResultRepository.recordHash(r2));
        assertNotEquals(NativeTaskResultRepository.recordHash(r1), NativeTaskResultRepository.recordHash(r3));
    }
}
