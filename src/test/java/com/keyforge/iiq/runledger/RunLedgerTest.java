package com.keyforge.iiq.runledger;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the extraction-run ledger's pure logic: per-entity accumulation + aggregate totals,
 * status derivation from exit code, error-reason mapping, entity-counts JSON, duration, and the
 * safe no-op behavior of the reporting API when no run is active. DB persistence is best-effort and
 * exercised at runtime, not here.
 */
class RunLedgerTest {

    private static RunContext ctx() {
        return new RunContext("11111111-1111-1111-1111-111111111111",
                "extract-users-db", "scim", Instant.parse("2026-09-15T10:00:00Z"));
    }

    @Test
    void aggregatesPerEntityCountsAndSucceeds() {
        RunContext c = ctx();
        c.addEntity("kf_identity", 68, 68, 0, 0);
        ExtractionRun run = RunLedger.buildRun(c, 0, Instant.parse("2026-09-15T10:00:03Z"));
        assertEquals("extract-users-db", run.command());
        assertEquals("scim", run.sourceInterface());
        assertEquals("SUCCESS", run.status());
        assertEquals(Integer.valueOf(68), run.extracted());
        assertEquals(Integer.valueOf(68), run.inserted());
        assertEquals(Integer.valueOf(0), run.updated());
        assertEquals(Integer.valueOf(0), run.failed());
        assertEquals(Long.valueOf(3000L), run.durationMs());
        assertNull(run.errorMessage());
        assertTrue(run.entityCountsJson().contains("\"entity\":\"kf_identity\""), run.entityCountsJson());
        assertTrue(run.entityCountsJson().contains("\"extracted\":68"), run.entityCountsJson());
    }

    @Test
    void multipleEntitiesSumIntoAggregates() {
        RunContext c = ctx();
        c.addEntity("kf_access_request", 45, 45, 0, 0);
        c.addEntity("kf_request_item", 88, 88, 0, 0);
        c.addEntity("kf_request_approval", 13, 13, 0, 0);
        ExtractionRun run = RunLedger.buildRun(c, 0, c.startedAt().plusMillis(10));
        assertEquals(Integer.valueOf(146), run.extracted());
        assertEquals(Integer.valueOf(146), run.inserted());
    }

    @Test
    void rowLevelFailuresMarkCompletedWithFailures() {
        RunContext c = ctx();
        c.addEntity("kf_identity", 68, 60, 0, 8);
        // exit 6 is the CLI's "row-level failures" code; also inferred when failed>0 on exit 0.
        ExtractionRun run = RunLedger.buildRun(c, 6, c.startedAt().plusMillis(1));
        assertEquals("COMPLETED_WITH_FAILURES", run.status());
        assertEquals(Integer.valueOf(8), run.failed());
    }

    @Test
    void failedRunGetsStatusAndReasonEvenWithNoCounts() {
        RunContext c = ctx();  // no entities recorded (failed before persisting)
        ExtractionRun run = RunLedger.buildRun(c, 5, c.startedAt().plusMillis(1));
        assertEquals("FAILED", run.status());
        assertEquals("11111111-1111-1111-1111-111111111111", run.extractionRunId());
        assertTrue(run.errorMessage().contains("PostgreSQL error"), run.errorMessage());
        assertTrue(run.errorMessage().contains("exit code 5"), run.errorMessage());
        assertNull(run.extracted());          // no per-entity counts → aggregate null
        assertNull(run.entityCountsJson());
    }

    @Test
    void explicitErrorMessageIsPreferredOverGenericReason() {
        RunContext c = ctx();
        c.setError("IdentityIQ login failed for user 'spadmin'");
        ExtractionRun run = RunLedger.buildRun(c, 4, c.startedAt().plusMillis(1));
        assertEquals("FAILED", run.status());
        assertEquals("IdentityIQ login failed for user 'spadmin'", run.errorMessage());
    }

    @Test
    void reportingApiIsSafeNoOpWhenNoRunActive() {
        // No begin(): these must not throw and must not start a run.
        RunLedger.record("kf_identity", 1, 1, 0, 0);
        RunLedger.error("ignored");
        RunLedger.window(Instant.now(), Instant.now());
        assertNull(RunLedger.currentRunId());
    }

    @Test
    void beginSetsRunIdAndFinishClearsIt() {
        RunLedger.begin("extract-task-results-db");
        assertTrue(RunLedger.currentRunId() != null);
        RunLedger.finish(0);   // best-effort persist (PG may be absent) then clears context
        assertNull(RunLedger.currentRunId());
    }
}
