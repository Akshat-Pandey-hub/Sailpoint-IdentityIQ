package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.parquet.ParquetIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Safety invariant for the kf_workgroup deletion sweep: it reuses {@code sweepEntityDeletions}, whose
 * keep-set is {@code ParquetIds.canonicalUuid(id)}, while rows are stored with
 * {@code WorkgroupRowMapper.toCanonicalUuid(id)} as {@code workgroupid}. This locks that the two
 * canonicalizations agree, so the sweep can never mark a live Workgroup deleted.
 */
class WorkgroupDeletionKeyTest {

    @Test
    void keepSetCanonicalizationEqualsStoredWorkgroupid() {
        for (String raw : new String[]{
                "7f00010198421229819849f9859c0e4a",
                "{7f00010198421229819849f9859c0e4a}",
                "7f000101-9842-1229-8198-49f9859c0e4a"}) {
            assertEquals(WorkgroupRowMapper.toCanonicalUuid(raw), ParquetIds.canonicalUuid(raw),
                    "keep-set id must equal the stored workgroupid for: " + raw);
        }
    }
}
