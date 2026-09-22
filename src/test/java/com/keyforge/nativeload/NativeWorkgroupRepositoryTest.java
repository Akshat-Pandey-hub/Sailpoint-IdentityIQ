package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native Workgroup persistence. */
class NativeWorkgroupRepositoryTest {

    private static NativeWorkgroupRecord record(String sourceId, String name) {
        NativeWorkgroupRecord r = new NativeWorkgroupRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void workgroupIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeWorkgroupRepository.canonicalWorkgroupId(record(hex, "Security Admins"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeWorkgroupRepository.canonicalWorkgroupId(record(null, "Security Admins"));
        String b = NativeWorkgroupRepository.canonicalWorkgroupId(record(null, "Security Admins"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithBusinessData() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeWorkgroupRecord r1 = record(hex, "Security Admins");
        r1.email = "sec@example.com";
        NativeWorkgroupRecord r2 = record(hex, "Security Admins");
        r2.email = "sec@example.com";
        NativeWorkgroupRecord r3 = record(hex, "Security Admins");
        r3.email = "other@example.com";
        assertNotNull(NativeWorkgroupRepository.recordHash(r1));
        assertEquals(NativeWorkgroupRepository.recordHash(r1), NativeWorkgroupRepository.recordHash(r2));
        assertNotEquals(NativeWorkgroupRepository.recordHash(r1), NativeWorkgroupRepository.recordHash(r3));
    }
}
