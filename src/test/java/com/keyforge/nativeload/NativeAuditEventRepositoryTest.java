package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic append-only PK + record-hash for native AuditEvent persistence. */
class NativeAuditEventRepositoryTest {

    private static NativeAuditEventRecord rec(String sourceId, String action) {
        NativeAuditEventRecord r = new NativeAuditEventRecord();
        r.sourceId = sourceId;
        r.action = action;
        r.auditSource = "spadmin";
        return r;
    }

    @Test
    void auditIdIsDeterministicCanonicalUuid() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeAuditEventRepository.canonicalAuditId(rec(hex, "Login"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
        // idempotent: same source id -> same PK (append-only re-run never duplicates)
        assertEquals(id, NativeAuditEventRepository.canonicalAuditId(rec(hex, "Login")));
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeAuditEventRepository.canonicalAuditId(rec(null, "Login"));
        assertNotNull(a);
        assertEquals(a, NativeAuditEventRepository.canonicalAuditId(rec(null, "Login")));
    }

    @Test
    void recordHashChangesWithAction() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        assertNotEquals(NativeAuditEventRepository.recordHash(rec(hex, "Login")),
                NativeAuditEventRepository.recordHash(rec(hex, "Logout")));
    }
}
