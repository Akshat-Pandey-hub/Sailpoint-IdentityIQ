package com.keyforge.iiq.countrecon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure, DB-free tests for the cross-pipeline count reconciliation catalog and comparison logic. */
class CountReconciliationTest {

    @Test
    void catalogIsWellFormed() {
        List<CountPair> pairs = CountReconciliation.all();
        assertFalse(pairs.isEmpty());
        Set<String> domains = new HashSet<>();
        for (CountPair p : pairs) {
            assertTrue(notBlank(p.domain()) && notBlank(p.pgTable()) && notBlank(p.parquetDataset()));
            assertTrue(domains.add(p.domain()), "duplicate domain: " + p.domain());
        }
        // the one intentional name divergence is captured correctly
        assertTrue(pairs.stream().anyMatch(p -> p.pgTable().equals("kf_task_result")
                && p.parquetDataset().equals("task_result")));
        // certifications are intentionally not paired (different object per store)
        assertFalse(domains.contains("certification"));
    }

    @Test
    void classifyCoversEveryOutcome() {
        assertEquals(CountReconciliation.MATCH, CountReconciliation.classify(45, 45));
        assertEquals(CountReconciliation.MISMATCH, CountReconciliation.classify(45, 44));
        assertEquals(CountReconciliation.MATCH, CountReconciliation.classify(0, 0));
        assertEquals(CountReconciliation.PG_ONLY, CountReconciliation.classify(10, -1));
        assertEquals(CountReconciliation.PARQUET_ONLY, CountReconciliation.classify(-1, 10));
        assertEquals(CountReconciliation.BOTH_ABSENT, CountReconciliation.classify(-1, -1));
    }

    @Test
    void pgCountSqlIsSchemaQualified() {
        assertEquals("SELECT count(*) FROM iiq_migration_final.kf_audit_event",
                CountReconciliation.pgCountSql("iiq_migration_final", "kf_audit_event"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
