package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native CertificationItem persistence. */
class NativeCertificationItemRepositoryTest {

    private static NativeCertificationItemRecord rec(String sourceId, String decision) {
        NativeCertificationItemRecord r = new NativeCertificationItemRecord();
        r.sourceId = sourceId;
        r.identity = "alice";
        r.exceptionApplication = "AD";
        r.exceptionAttributeName = "memberOf";
        r.exceptionAttributeValue = "cn=admins";
        r.actionStatus = decision;
        return r;
    }

    @Test
    void itemIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeCertificationItemRepository.canonicalItemId(rec(hex, "Approved"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void recordHashChangesWhenDecisionChanges() {
        String h1 = NativeCertificationItemRepository.recordHash(rec("s1", "Approved"));
        String h1b = NativeCertificationItemRepository.recordHash(rec("s1", "Approved"));
        String h2 = NativeCertificationItemRepository.recordHash(rec("s1", "Remediated"));
        assertNotNull(h1);
        assertEquals(h1, h1b);
        assertNotEquals(h1, h2, "action status is business content and must change the hash");
    }
}
