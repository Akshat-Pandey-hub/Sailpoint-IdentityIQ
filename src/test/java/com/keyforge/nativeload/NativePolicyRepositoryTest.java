package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native Policy persistence. */
class NativePolicyRepositoryTest {

    private static NativePolicyRecord rec(String sourceId, String type) {
        NativePolicyRecord r = new NativePolicyRecord();
        r.sourceId = sourceId;
        r.name = "SoD Policy";
        r.type = type;
        return r;
    }

    @Test
    void policyIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativePolicyRepository.canonicalPolicyId(rec(hex, "SOD"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativePolicyRepository.canonicalPolicyId(rec(null, "SOD"));
        String b = NativePolicyRepository.canonicalPolicyId(rec(null, "SOD"));
        assertNotNull(a);
        assertEquals(a, b);
    }

    @Test
    void recordHashChangesWithType() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        assertNotEquals(NativePolicyRepository.recordHash(rec(hex, "SOD")),
                NativePolicyRepository.recordHash(rec(hex, "Activity")));
    }

    @Test
    void recordHashChangesWithViolationOwnerTypeAndTemplate() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String base = NativePolicyRepository.recordHash(rec(hex, "SOD"));

        NativePolicyRecord withType = rec(hex, "SOD");
        withType.violationOwnerType = "Rule";
        assertNotEquals(base, NativePolicyRepository.recordHash(withType),
                "violation_owner_type participates in the hash");

        NativePolicyRecord withTemplate = rec(hex, "SOD");
        withTemplate.template = Boolean.TRUE;
        assertNotEquals(base, NativePolicyRepository.recordHash(withTemplate),
                "is_template participates in the hash");

        NativePolicyRecord withRule = rec(hex, "SOD");
        withRule.violationOwnerRuleName = "Owner Rule";
        assertNotEquals(base, NativePolicyRepository.recordHash(withRule),
                "violation_owner_rule_name participates in the hash");
    }
}
