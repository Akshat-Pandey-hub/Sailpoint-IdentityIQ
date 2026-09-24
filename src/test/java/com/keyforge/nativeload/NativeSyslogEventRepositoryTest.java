package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic append-only PK + record-hash for native SyslogEvent persistence. */
class NativeSyslogEventRepositoryTest {

    private static NativeSyslogEventRecord rec(String sourceId, String level) {
        NativeSyslogEventRecord r = new NativeSyslogEventRecord();
        r.sourceId = sourceId;
        r.eventLevel = level;
        r.server = "iiq01";
        return r;
    }

    @Test
    void syslogIdIsDeterministicCanonicalUuid() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeSyslogEventRepository.canonicalSyslogId(rec(hex, "ERROR"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals(id, NativeSyslogEventRepository.canonicalSyslogId(rec(hex, "ERROR")));
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeSyslogEventRepository.canonicalSyslogId(rec(null, "ERROR"));
        assertNotNull(a);
        assertEquals(a, NativeSyslogEventRepository.canonicalSyslogId(rec(null, "ERROR")));
    }

    @Test
    void recordHashChangesWithMessage() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        NativeSyslogEventRecord r1 = rec(hex, "ERROR");
        r1.message = "one";
        NativeSyslogEventRecord r2 = rec(hex, "ERROR");
        r2.message = "two";
        assertNotEquals(NativeSyslogEventRepository.recordHash(r1), NativeSyslogEventRepository.recordHash(r2));
    }
}
