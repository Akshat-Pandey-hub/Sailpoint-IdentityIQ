package com.keyforge.iiq.application;

import com.keyforge.iiq.parquet.ParquetIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Safety invariant for the applicationinstance deletion sweep: it reuses {@code sweepEntityDeletions},
 * whose keep-set is {@code ParquetIds.canonicalUuid(application.getId())}, while rows are stored with
 * {@code ApplicationInstanceRowMapper.toCanonicalUuid(application.getId())} as {@code instanceid}. This
 * locks that the two canonicalizations agree, so the sweep can never mark a live instance deleted, and
 * that unusable ids drop out of the keep-set (never persisted → never a false deletion).
 */
class ApplicationInstanceDeletionKeyTest {

    @Test
    void keepSetCanonicalizationEqualsStoredInstanceid() {
        for (String raw : new String[]{
                "7f0001019eb91d3c819f01f0c4127efb",
                "{7f0001019eb91d3c819f01f0c4127efb}",
                "7f000101-9eb9-1d3c-819f-01f0c4127efb"}) {
            assertEquals(ApplicationInstanceRowMapper.toCanonicalUuid(raw), ParquetIds.canonicalUuid(raw),
                    "keep-set id must equal the stored instanceid for: " + raw);
        }
    }

    @Test
    void invalidOrNullIdsAreExcludedFromKeepSet() {
        // The keep-set builder uses ParquetIds.canonicalUuid, which returns null for unusable ids;
        // Main.sweepEntityDeletions drops nulls, so such records never cause a deletion.
        assertNull(ParquetIds.canonicalUuid(null));
        assertNull(ParquetIds.canonicalUuid(""));
        assertNull(ParquetIds.canonicalUuid("not-a-uuid"));
    }
}
