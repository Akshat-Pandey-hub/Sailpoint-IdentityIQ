package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native Application persistence. */
class NativeApplicationRepositoryTest {

    private static NativeApplicationRecord record(String sourceId, String name) {
        NativeApplicationRecord r = new NativeApplicationRecord();
        r.sourceId = sourceId;
        r.name = name;
        return r;
    }

    @Test
    void applicationIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeApplicationRepository.canonicalApplicationId(record(hex, "AD"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeApplicationRepository.canonicalApplicationId(record(null, "AD"));
        String b = NativeApplicationRepository.canonicalApplicationId(record(null, "AD"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashIsDeterministicAndChangesWithBusinessData() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeApplicationRecord r1 = record(hex, "AD");
        r1.type = "ActiveDirectory";
        NativeApplicationRecord r2 = record(hex, "AD");
        r2.type = "ActiveDirectory";
        NativeApplicationRecord r3 = record(hex, "AD");
        r3.type = "LDAP";
        assertNotNull(NativeApplicationRepository.recordHash(r1));
        assertEquals(NativeApplicationRepository.recordHash(r1), NativeApplicationRepository.recordHash(r2));
        assertNotEquals(NativeApplicationRepository.recordHash(r1), NativeApplicationRepository.recordHash(r3));
    }

    @Test
    void ruleReferenceIdParticipatesInBusinessHash() {
        NativeApplicationRecord a = record("source-1", "AD");
        NativeApplicationRecord b = record("source-1", "AD");
        a.applicationCreationRule = b.applicationCreationRule = "CreateRule";
        a.applicationCreationRuleId = "rule-id-1";
        b.applicationCreationRuleId = "rule-id-2";
        assertNotEquals(NativeApplicationRepository.recordHash(a), NativeApplicationRepository.recordHash(b));
    }
}
