package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeWorkItemArchiveRepositoryTest {
    private NativeWorkItemArchiveRecord record(String id) {
        NativeWorkItemArchiveRecord r = new NativeWorkItemArchiveRecord();
        r.sourceId = id;
        r.workItemId = "original-work-item";
        r.name = "archived item";
        r.type = "Approval";
        r.srcEventTs = java.time.Instant.parse("2026-09-01T10:11:12Z");
        return r;
    }

    @Test
    void primaryKeyComesOnlyFromArchiveSourceId() {
        String id = "7f0001019f061fdc819fa50301271752";
        assertEquals(ParquetIds.canonicalUuid(id), NativeWorkItemArchiveRepository.canonicalArchiveId(record(id)));
        NativeWorkItemArchiveRecord missing = record(null);
        assertThrows(IllegalArgumentException.class, () -> NativeWorkItemArchiveRepository.canonicalArchiveId(missing));
    }

    @Test
    void hashExcludesRunMetadataAndAppendSqlIsImmutable() {
        NativeWorkItemArchiveRecord a = record("7f0001019f061fdc819fa50301271752");
        NativeWorkItemArchiveRecord b = record(a.sourceId);
        a.extractionRunId = "run-a";
        b.extractionRunId = "run-b";
        a.extractedAt = java.time.Instant.EPOCH;
        b.extractedAt = java.time.Instant.now();
        assertEquals(NativeWorkItemArchiveRepository.recordHash(a), NativeWorkItemArchiveRepository.recordHash(b));
        NativeWorkItemArchiveRepository repository = new NativeWorkItemArchiveRepository("iiq_native");
        assertEquals("iiq_native.kf_workitem_archive", repository.targetTable());
        String sql = NativeWorkItemArchiveRepository.appendSql(repository.targetTable());
        assertTrue(sql.contains("ON CONFLICT (archiveid) DO NOTHING"));
        assertFalse(sql.contains("DO UPDATE"));
        assertFalse(sql.contains("DELETE"));
        assertTrue(sql.contains("archiveid, source_id, work_item_id"));
        assertTrue(sql.contains("src_event_ts"));
    }
}
