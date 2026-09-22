package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native Role persistence. */
class NativeRoleRepositoryTest {

    private static NativeRoleRecord record(String sourceId, String name) {
        NativeRoleRecord r = new NativeRoleRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void roleIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeRoleRepository.canonicalRoleId(record(hex, "Engineer"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeRoleRepository.canonicalRoleId(record(null, "Engineer"));
        String b = NativeRoleRepository.canonicalRoleId(record(null, "Engineer"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithBusinessData() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeRoleRecord r1 = record(hex, "Engineer");
        r1.type = "business";
        NativeRoleRecord r2 = record(hex, "Engineer");
        r2.type = "business";
        NativeRoleRecord r3 = record(hex, "Engineer");
        r3.type = "it";
        assertNotNull(NativeRoleRepository.recordHash(r1));
        assertEquals(NativeRoleRepository.recordHash(r1), NativeRoleRepository.recordHash(r2));
        assertNotEquals(NativeRoleRepository.recordHash(r1), NativeRoleRepository.recordHash(r3));
    }
}
