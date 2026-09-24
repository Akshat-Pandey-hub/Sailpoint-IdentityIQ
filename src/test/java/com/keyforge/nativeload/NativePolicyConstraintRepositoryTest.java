package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native policy-constraint persistence. */
class NativePolicyConstraintRepositoryTest {

    private static NativePolicyConstraintRecord rec(String sourceId, String policyId, String type, String name) {
        NativePolicyConstraintRecord r = new NativePolicyConstraintRecord();
        r.sourceId = sourceId;
        r.policyId = policyId;
        r.constraintType = type;
        r.name = name;
        return r;
    }

    @Test
    void constraintIdIsCanonicalUuidOfConstraintId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativePolicyConstraintRepository.canonicalConstraintId(rec(hex, "pol-1", "SOD", "c"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void fallbackIsDeterministicAndTypeDisambiguates() {
        String a = NativePolicyConstraintRepository.canonicalConstraintId(rec(null, "pol-1", "SOD", "c"));
        String b = NativePolicyConstraintRepository.canonicalConstraintId(rec(null, "pol-1", "SOD", "c"));
        String g = NativePolicyConstraintRepository.canonicalConstraintId(rec(null, "pol-1", "GENERIC", "c"));
        assertNotNull(a);
        assertEquals(a, b, "same constraint ⇒ same id");
        assertNotEquals(a, g, "type disambiguates SOD vs GENERIC of the same policy");
    }

    @Test
    void recordHashChangesWithBundles() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativePolicyConstraintRecord r1 = rec(hex, "pol-1", "SOD", "c");
        r1.leftBundlesJson = "[{\"id\":\"r1\"}]";
        NativePolicyConstraintRecord r2 = rec(hex, "pol-1", "SOD", "c");
        r2.leftBundlesJson = "[{\"id\":\"r9\"}]";
        assertNotEquals(NativePolicyConstraintRepository.recordHash(r1),
                NativePolicyConstraintRepository.recordHash(r2));
    }
}
