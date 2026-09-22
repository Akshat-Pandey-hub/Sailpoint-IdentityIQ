package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the append-only invariant (PDF §7.3): the {@code kf_event} write is strictly
 * {@code INSERT ... ON CONFLICT (event_id) DO NOTHING}, and the repository's write SQL contains no
 * UPDATE/DELETE of existing events. Also checks the envelope columns exist in the DDL.
 */
class EventAppendOnlyTest {

    @Test
    void appendSqlIsInsertOnConflictDoNothing() {
        String sql = EventRepository.appendSql("s.kf_event");
        String upper = sql.toUpperCase(Locale.ROOT);
        assertTrue(upper.contains("INSERT INTO S.KF_EVENT"), "insert into target");
        assertTrue(upper.contains("ON CONFLICT (EVENT_ID) DO NOTHING"), "append-only dedup");
        assertTrue(upper.contains("RETURNING EVENT_ID"), "reports whether a row was inserted");
        assertFalse(upper.contains("DO UPDATE"), "must never update an existing event");
        assertFalse(upper.contains("DELETE"), "must never delete an event");
        assertTrue(sql.contains(", NULL, ?::jsonb)"), "raw_ref written as literal NULL");
    }

    @Test
    void createTableHasFullEventEnvelope() {
        String ddl = EventRepository.createTableSql("s.kf_event");
        for (String col : new String[]{"event_id uuid PRIMARY KEY", "src_system", "src_object_type",
                "src_object_id", "event_type", "event_fingerprint", "src_event_ts timestamptz",
                "src_event_ts_precision", "extraction_run_id", "src_interface", "raw_ref",
                "event_detail jsonb", "extracted_at timestamptz"}) {
            assertTrue(ddl.contains(col), "DDL missing: " + col);
        }
    }
}
