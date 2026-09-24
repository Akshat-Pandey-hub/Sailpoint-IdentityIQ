package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Page-by-policy orchestration: scan guard, empty-constraint policies, and soft-delete sweep. */
class NativePolicyConstraintImportServiceTest {

    private static final class FakePages implements NativePolicyConstraintPageSource {
        private final List<String> pages;
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"returnedPolicies\":0,\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativePolicyConstraintSink {
        Collection<String> swept;
        @Override public void ensure() { }
        @Override public NativePolicyConstraintRepository.UpsertOutcome upsert(NativePolicyConstraintRecord r) {
            return NativePolicyConstraintRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.swept = keep;
            return new SoftDeleteSweeper.SweepResult("kf_policy_constraint", keep.size(), 0, 0, keep.isEmpty(), null);
        }
    }

    private static String c(String id, String policy, String type) {
        return "{\"sourceId\":\"" + id + "\",\"policyId\":\"" + policy + "\",\"constraintType\":\"" + type + "\"}";
    }

    @Test
    void pagesByPolicyAndSweepsWhenScanComplete() throws SQLException {
        // 2 policies total, page size 2: one page covers both; policy p2 has zero constraints (must not stop scan)
        FakeSink sink = new FakeSink();
        NativePolicyConstraintImportService.Result r = new NativePolicyConstraintImportService(
                new FakePages(List.of("{\"sourceCount\":2,\"returnedPolicies\":2,\"rows\":["
                        + c("c1", "p1", "SOD") + "]}")), sink, 2, true).importAll();
        assertEquals(1, r.getExtracted());
        assertEquals(2, r.getScannedPolicies());
        assertTrue(r.isSweepRan());
        assertEquals(1, sink.swept.size());
    }

    @Test
    void incompletePolicyScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativePolicyConstraintImportService(
                new FakePages(List.of("{\"sourceCount\":5,\"returnedPolicies\":1,\"rows\":["
                        + c("c1", "p1", "SOD") + "]}")), sink, 2, true).importAll());
        assertNull(sink.swept, "sweep must not run on an incomplete scan");
    }
}
