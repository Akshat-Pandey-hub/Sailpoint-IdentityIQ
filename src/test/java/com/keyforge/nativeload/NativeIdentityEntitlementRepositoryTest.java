package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native IdentityEntitlement persistence. */
class NativeIdentityEntitlementRepositoryTest {

    private static NativeIdentityEntitlementRecord rec(String sourceId) {
        NativeIdentityEntitlementRecord r = new NativeIdentityEntitlementRecord();
        r.sourceId = sourceId;
        r.identityId = "id-1";
        r.applicationName = "AD";
        r.nativeIdentity = "cn=alice";
        r.attributeName = "memberOf";
        r.attributeValue = "cn=admins";
        return r;
    }

    @Test
    void entitlementIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeIdentityEntitlementRepository.canonicalEntitlementId(rec(hex));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void missingSourceIdFallsBackToStableCompositeUuid() {
        String a = NativeIdentityEntitlementRepository.canonicalEntitlementId(rec(null));
        String b = NativeIdentityEntitlementRepository.canonicalEntitlementId(rec(null));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashChangesWhenProvenanceChanges() {
        NativeIdentityEntitlementRecord base = rec("s1");
        base.grantedByRole = Boolean.FALSE;
        NativeIdentityEntitlementRecord same = rec("s1");
        same.grantedByRole = Boolean.FALSE;
        NativeIdentityEntitlementRecord changed = rec("s1");
        changed.grantedByRole = Boolean.TRUE;

        String h1 = NativeIdentityEntitlementRepository.recordHash(base);
        assertEquals(h1, NativeIdentityEntitlementRepository.recordHash(same));
        assertNotEquals(h1, NativeIdentityEntitlementRepository.recordHash(changed),
                "granted-by-role is business content and must change the hash");
    }
}
