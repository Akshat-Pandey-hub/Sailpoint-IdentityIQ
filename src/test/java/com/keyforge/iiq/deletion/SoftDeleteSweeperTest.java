package com.keyforge.iiq.deletion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure, DB-free tests for the reusable soft-delete sweep SQL + the safety-critical id normalization. */
class SoftDeleteSweeperTest {

    @Test
    void markSqlOnlyTouchesRowsAbsentFromSourceAndNotAlreadyDeleted() {
        String sql = SoftDeleteSweeper.markSql("s.kf_task_result", "taskresultid");
        assertTrue(sql.startsWith("UPDATE s.kf_task_result SET is_deleted = true, deleted_at = now()"));
        assertTrue(sql.contains("WHERE NOT (taskresultid = ANY(?))"));
        assertTrue(sql.contains("AND is_deleted = false"), "idempotent: never re-mark");
    }

    @Test
    void reviveSqlRestoresReappearedRows() {
        String sql = SoftDeleteSweeper.reviveSql("s.kf_task_result", "taskresultid");
        assertTrue(sql.contains("SET is_deleted = false, deleted_at = NULL"));
        assertTrue(sql.contains("WHERE taskresultid = ANY(?) AND is_deleted = true"));
    }

    @Test
    void addColumnStatementsAreIdempotentAndAdditive() {
        assertTrue(SoftDeleteSweeper.addIsDeletedSql("s.t")
                .equals("ALTER TABLE s.t ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false"));
        assertTrue(SoftDeleteSweeper.addDeletedAtSql("s.t")
                .equals("ALTER TABLE s.t ADD COLUMN IF NOT EXISTS deleted_at timestamptz"));
    }

    @Test
    void normalizeDropsNullBlankAndDuplicates() {
        Set<String> ids = SoftDeleteSweeper.normalizeIds(Arrays.asList("a", "a", " b ", "", null, "  "));
        assertEquals(Set.of("a", "b"), ids);
    }

    @Test
    void emptySourceNormalizesToEmpty_triggeringTheSafetySkip() {
        // The safety guard: an empty/failed source fetch must never mark a whole table deleted.
        assertTrue(SoftDeleteSweeper.normalizeIds(null).isEmpty());
        assertTrue(SoftDeleteSweeper.normalizeIds(List.of()).isEmpty());
        assertTrue(SoftDeleteSweeper.normalizeIds(Arrays.asList("", "   ", (String) null)).isEmpty());
    }
}
