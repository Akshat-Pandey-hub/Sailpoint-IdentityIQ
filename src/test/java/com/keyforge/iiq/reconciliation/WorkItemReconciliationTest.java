package com.keyforge.iiq.reconciliation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB-free tests for the WorkItem reconciliation SQL builders — verifies the joins are schema-qualified
 * and reference the correct explicit id columns ({@code kf_workitem.source_id} and
 * {@code kf_workitem_archive.work_item_id}), never name/timestamp/order. Mirrors {@link ReconciliationChecksTest}.
 */
class WorkItemReconciliationTest {

    private static final String S = "iiq_native";

    @Test
    void countSqlIsSchemaQualified() {
        assertEquals("SELECT count(*) FROM iiq_native.kf_workitem",
                WorkItemReconciliation.countSql(S, WorkItemReconciliation.WORKITEM));
        assertEquals("SELECT count(*) FROM iiq_native.kf_workitem_archive",
                WorkItemReconciliation.countSql(S, WorkItemReconciliation.ARCHIVE));
    }

    @Test
    void distinctRefsSqlExcludesNullAndBlank() {
        String sql = WorkItemReconciliation.distinctRefsSql(S);
        assertTrue(sql.contains("count(DISTINCT work_item_id)"), sql);
        assertTrue(sql.contains("iiq_native.kf_identity_request_approval"), sql);
        assertTrue(sql.contains("work_item_id IS NOT NULL AND work_item_id <> ''"), sql);
    }

    @Test
    void matchedEitherJoinsOnExplicitIdColumns() {
        String sql = WorkItemReconciliation.matchedEitherSql(S);
        assertTrue(sql.contains("w.source_id = r.wid"), sql);           // live WorkItem id
        assertTrue(sql.contains("a.work_item_id = r.wid"), sql);        // archived WorkItem id
        assertTrue(sql.contains("iiq_native.kf_workitem "), sql);
        assertTrue(sql.contains("iiq_native.kf_workitem_archive "), sql);
        assertTrue(sql.contains(" OR EXISTS"), sql);
    }

    @Test
    void matchedLiveAndArchiveAreDistinctChecks() {
        assertTrue(WorkItemReconciliation.matchedLiveSql(S).contains("w.source_id = r.wid"));
        assertTrue(!WorkItemReconciliation.matchedLiveSql(S).contains("kf_workitem_archive"));
        assertTrue(WorkItemReconciliation.matchedArchiveSql(S).contains("a.work_item_id = r.wid"));
        assertTrue(!WorkItemReconciliation.matchedArchiveSql(S).contains(".kf_workitem "));
    }

    @Test
    void unmatchedSamplesUsesDoubleNotExistsAndLimit() {
        String sql = WorkItemReconciliation.unmatchedSamplesSql(S, 25);
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM iiq_native.kf_workitem "), sql);
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM iiq_native.kf_workitem_archive "), sql);
        assertTrue(sql.contains("LIMIT 25"), sql);
    }

    @Test
    void resultDefaultsAreZero() {
        WorkItemReconciliation.Result r = new WorkItemReconciliation.Result();
        assertEquals(0, r.approvalRefs);
        assertEquals(0, r.unmatched);
        assertTrue(r.unmatchedSamples.isEmpty());
    }

    @Test
    void determinationIsNeutralAndNeverAssertsPruning() {
        WorkItemReconciliation.Result r = new WorkItemReconciliation.Result();
        r.approvalRefs = 11;
        r.workItemArchives = 0;
        r.unmatched = 11;
        String d = WorkItemReconciliation.buildDetermination(r);
        // exact evidence-only wording, no cause asserted
        assertTrue(d.contains("do not resolve to a live WorkItem or WorkItemArchive record"), d);
        assertTrue(d.contains("zero WorkItemArchive records"), d);
        assertTrue(d.contains("unavailable in the extracted IIQ data"), d);
        assertTrue(!d.toLowerCase().contains("prune"), d);
        assertTrue(!d.toLowerCase().contains("housekeeping"), d);
    }

    @Test
    void determinationAllMatchedAndEmpty() {
        WorkItemReconciliation.Result matched = new WorkItemReconciliation.Result();
        matched.approvalRefs = 5;
        matched.unmatched = 0;
        assertTrue(WorkItemReconciliation.buildDetermination(matched).contains("resolve to a live or archived WorkItem"));

        WorkItemReconciliation.Result none = new WorkItemReconciliation.Result();
        assertTrue(WorkItemReconciliation.buildDetermination(none).contains("nothing to reconcile"));
    }
}
