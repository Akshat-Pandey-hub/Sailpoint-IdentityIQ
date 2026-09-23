package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash for native CertificationArchive (append-only historical). */
class NativeCertificationArchiveRepositoryTest {

    private static NativeCertificationArchiveRecord rec(String sourceId, String xml) {
        NativeCertificationArchiveRecord r = new NativeCertificationArchiveRecord();
        r.sourceId = sourceId;
        r.certificationId = "c1";
        r.archiveXml = xml;
        return r;
    }

    @Test
    void archiveIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeCertificationArchiveRepository.canonicalArchiveId(rec(hex, "<x/>"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void recordHashChangesWithHistoricalEvidence() {
        String h1 = NativeCertificationArchiveRepository.recordHash(rec("s1", "<a/>"));
        String h2 = NativeCertificationArchiveRepository.recordHash(rec("s1", "<b/>"));
        assertNotNull(h1);
        assertNotEquals(h1, h2, "archive_xml is business evidence and must change the hash");
    }
}
