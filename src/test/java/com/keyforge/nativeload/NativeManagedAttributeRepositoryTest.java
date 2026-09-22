package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native ManagedAttribute persistence. */
class NativeManagedAttributeRepositoryTest {

    private static NativeManagedAttributeRecord record(String sourceId, String value) {
        NativeManagedAttributeRecord r = new NativeManagedAttributeRecord();
        r.sourceId = sourceId;
        r.value = value;
        r.name = value;
        return r;
    }

    @Test
    void entitlementIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeManagedAttributeRepository.canonicalEntitlementId(record(hex, "CN=Admins"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString(), "must be a valid canonical UUID");
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeManagedAttributeRepository.canonicalEntitlementId(record(null, "CN=Admins"));
        String b = NativeManagedAttributeRepository.canonicalEntitlementId(record(null, "CN=Admins"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithBusinessData() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String h1 = NativeManagedAttributeRepository.recordHash(record(hex, "CN=Admins"));
        String h2 = NativeManagedAttributeRepository.recordHash(record(hex, "CN=Admins"));
        String h3 = NativeManagedAttributeRepository.recordHash(record(hex, "CN=Users"));
        assertNotNull(h1);
        assertEquals(h1, h2, "same business data → same hash");
        assertNotEquals(h1, h3, "changed business data → different hash");
    }
}
