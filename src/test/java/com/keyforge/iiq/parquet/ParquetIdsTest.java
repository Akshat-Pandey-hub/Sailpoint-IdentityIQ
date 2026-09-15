package com.keyforge.iiq.parquet;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParquetIdsTest {

    @Test
    void canonicalUuidMatchesDbConvention() {
        assertEquals("7f000101-9f06-1fdc-819f-a50301271752",
                ParquetIds.canonicalUuid("7f0001019f061fdc819fa50301271752"));
        assertEquals("7f000101-9f06-1fdc-819f-a50301271752",
                ParquetIds.canonicalUuid("{7f0001019f061fdc819fa50301271752}"));
        assertNull(ParquetIds.canonicalUuid(null));
        assertNull(ParquetIds.canonicalUuid("  "));
        assertNull(ParquetIds.canonicalUuid("not-a-guid")); // lenient: null, raw kept elsewhere
    }

    @Test
    void deterministicUuidIsStable() {
        String a = ParquetIds.deterministicUuid("AUDIT_TARGET|x|y");
        String b = ParquetIds.deterministicUuid("AUDIT_TARGET|x|y");
        assertEquals(a, b);
    }

    @Test
    void timeHelpers() {
        assertEquals(Instant.ofEpochMilli(1786845600405L), ParquetIds.epochMillis(1786845600405L));
        assertNull(ParquetIds.epochMillis(null));
        assertEquals(Instant.parse("2025-07-27T04:00:28.298Z"),
                ParquetIds.iso("2025-07-27T04:00:28.298Z"));
        assertNull(ParquetIds.iso(null));
    }
}
