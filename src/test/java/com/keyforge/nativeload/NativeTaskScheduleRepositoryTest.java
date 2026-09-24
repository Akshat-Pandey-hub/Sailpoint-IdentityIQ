package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native TaskSchedule persistence. */
class NativeTaskScheduleRepositoryTest {

    private static NativeTaskScheduleRecord record(String sourceId, String name) {
        NativeTaskScheduleRecord r = new NativeTaskScheduleRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void taskScheduleIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeTaskScheduleRepository.canonicalTaskScheduleId(record(hex, "Nightly HR Aggregation"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeTaskScheduleRepository.canonicalTaskScheduleId(record(null, "Nightly HR Aggregation"));
        String b = NativeTaskScheduleRepository.canonicalTaskScheduleId(record(null, "Nightly HR Aggregation"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithCron() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeTaskScheduleRecord r1 = record(hex, "Nightly HR Aggregation");
        r1.cronExpressionsJson = "[\"0 0 1 * * ?\"]";
        NativeTaskScheduleRecord r2 = record(hex, "Nightly HR Aggregation");
        r2.cronExpressionsJson = "[\"0 0 1 * * ?\"]";
        NativeTaskScheduleRecord r3 = record(hex, "Nightly HR Aggregation");
        r3.cronExpressionsJson = "[\"0 0 2 * * ?\"]";
        assertNotNull(NativeTaskScheduleRepository.recordHash(r1));
        assertEquals(NativeTaskScheduleRepository.recordHash(r1), NativeTaskScheduleRepository.recordHash(r2));
        assertNotEquals(NativeTaskScheduleRepository.recordHash(r1), NativeTaskScheduleRepository.recordHash(r3));
    }
}
