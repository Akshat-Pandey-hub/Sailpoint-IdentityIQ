package com.keyforge.iiq.parquet;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LineageTest {

    private static Map<String, Object> business() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "7f00");
        m.put("name", "Alice");
        m.put("value", null);
        return m;
    }

    @Test
    void stampAddsTwelveLineageColumns() {
        Map<String, Object> m = Lineage.stamp(business(),
                new Lineage.Envelope("Identity", "7f00", "alice", null, null, null, "scim", null),
                "run-1", Instant.parse("2026-09-16T00:00:00Z"));
        for (Column c : Lineage.COLUMNS) {
            assertEquals(true, m.containsKey(c.name()), "missing lineage column " + c.name());
        }
        assertEquals("IdentityIQ", m.get("src_system"));
        assertEquals("Identity", m.get("src_object_type"));
        assertEquals("7f00", m.get("src_object_id"));
        assertEquals("run-1", m.get("extraction_run_id"));
        assertEquals("scim", m.get("src_interface"));
        assertNull(m.get("src_event_ts"));
    }

    @Test
    void recordHashIsDeterministicAndIndependentOfRunMetadata() {
        String h1 = Lineage.recordHash(business());
        String h2 = Lineage.recordHash(business());
        assertEquals(h1, h2, "same business fields must hash the same across runs");

        // Changing a business value changes the hash.
        Map<String, Object> changed = business();
        changed.put("name", "Bob");
        assertNotEquals(h1, Lineage.recordHash(changed));
    }
}
