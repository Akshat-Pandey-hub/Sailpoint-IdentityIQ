package com.keyforge.iiq.deletion;

import com.keyforge.iiq.model.Identity;
import com.keyforge.iiq.parquet.ParquetIds;
import com.keyforge.iiq.user.UserRowMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Safety invariant for CSS deletion detection: the keep-set is built with
 * {@link ParquetIds#canonicalUuid} while rows are stored with each RowMapper's {@code toCanonicalUuid}.
 * If those two disagreed, the sweep could mark a live row deleted. This locks that they produce the
 * <b>same</b> canonical PK, and that unparseable ids yield null (safely skipped, never persisted).
 */
class DeletionKeepSetTest {

    @Test
    void keepSetCanonicalizationEqualsStoredPk() {
        String raw = "7f00010198421229819849f9859c0e4a";
        Identity identity = new Identity(raw, "u", "Display", Boolean.TRUE, "e@x", "f", "l", List.of());

        // stored PK (what the repository persists) vs keep-set value (what the sweep compares against)
        String storedPk = UserRowMapper.map(identity).userid();
        String keepSetValue = ParquetIds.canonicalUuid(raw);

        assertEquals(storedPk, keepSetValue, "keep-set id must equal the stored PK, or the sweep is unsafe");
        assertEquals("7f000101-9842-1229-8198-49f9859c0e4a", keepSetValue);
    }

    @Test
    void bracesAndDashedFormsCanonicalizeIdentically() {
        String raw = "7f00010198421229819849f9859c0e4a";
        String dashed = "7f000101-9842-1229-8198-49f9859c0e4a";
        assertEquals(dashed, ParquetIds.canonicalUuid(raw));
        assertEquals(dashed, ParquetIds.canonicalUuid("{" + raw + "}"));
        assertEquals(dashed, ParquetIds.canonicalUuid(dashed));
    }

    @Test
    void unparseableIdsYieldNullAndAreSkipped() {
        assertNull(ParquetIds.canonicalUuid(null));
        assertNull(ParquetIds.canonicalUuid(""));
        assertNull(ParquetIds.canonicalUuid("not-a-uuid"));
    }
}
