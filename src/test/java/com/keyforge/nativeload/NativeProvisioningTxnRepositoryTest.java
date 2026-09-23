package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deterministic PK + record-hash for native ProvisioningTransaction persistence. */
class NativeProvisioningTxnRepositoryTest {

    private static NativeProvisioningTxnRecord rec(String sourceId, String status) {
        NativeProvisioningTxnRecord r = new NativeProvisioningTxnRecord();
        r.sourceId = sourceId;
        r.identityName = "alice";
        r.applicationName = "AD";
        r.operation = "Modify";
        r.status = status;
        return r;
    }

    @Test
    void txnIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeProvisioningTxnRepository.canonicalTxnId(rec(hex, "Pending"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void recordHashChangesWhenStatusChanges() {
        // A mutable transaction: Pending -> Committed must change the hash so an update fires.
        String h1 = NativeProvisioningTxnRepository.recordHash(rec("s1", "Pending"));
        String h1b = NativeProvisioningTxnRepository.recordHash(rec("s1", "Pending"));
        String h2 = NativeProvisioningTxnRepository.recordHash(rec("s1", "Committed"));
        assertNotNull(h1);
        assertEquals(h1, h1b);
        assertNotEquals(h1, h2, "status is mutable business content and must change the hash");
    }

    @Test
    void missingSourceIdDoesNotFallBackToName() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeProvisioningTxnRepository.canonicalTxnId(rec(null, "Pending")));
    }
}
