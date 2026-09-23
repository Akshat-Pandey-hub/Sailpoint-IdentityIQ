package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests for {@link NativeIdentityEntitlementImportService} using fakes — no IIQ, no DB. */
class NativeIdentityEntitlementImportServiceTest {

    private static final String HEX1 = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX2 = "1a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final class FakePages implements NativeIdentityEntitlementPageSource {
        private final List<String> pages;
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativeIdentityEntitlementSink {
        Collection<String> sweptKeep;
        @Override public void ensure() { }
        @Override public NativeIdentityEntitlementRepository.UpsertOutcome upsert(NativeIdentityEntitlementRecord r) {
            return NativeIdentityEntitlementRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            return new SoftDeleteSweeper.SweepResult("kf_identity_entitlement", keep.size(), 0, 0, false, null);
        }
    }

    private static String row(String hex) {
        return "{\"sourceId\":\"" + hex + "\",\"identityName\":\"alice\",\"attributeName\":\"memberOf\"}";
    }

    @Test
    void persistsAndSweepsWhenScanMatchesSourceCount() throws SQLException {
        FakePages src = new FakePages(List.of("{\"sourceCount\":2,\"rows\":["
                + row(HEX1) + "," + row(HEX2) + "]}"));
        FakeSink sink = new FakeSink();
        NativeIdentityEntitlementImportService.Result r =
                new NativeIdentityEntitlementImportService(src, sink, 100, true).importAll();

        assertEquals(2, r.getExtracted());
        assertEquals(2, r.getInserted());
        assertEquals(2, r.getSourceCount());
        assertTrue(r.isSweepRan());
        assertEquals(2, sink.sweptKeep.size());
    }

    @Test
    void incompleteScanThrowsBeforeSweeping() {
        // sourceCount says 5 but only 2 rows are returned → must fail and never sweep.
        FakePages src = new FakePages(List.of("{\"sourceCount\":5,\"rows\":["
                + row(HEX1) + "," + row(HEX2) + "]}"));
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class,
                () -> new NativeIdentityEntitlementImportService(src, sink, 100, true).importAll());
        assertNull(sink.sweptKeep, "sweep must not run on an incomplete scan");
    }

    @Test
    void sweepDisabledDoesNotSweep() throws SQLException {
        FakePages src = new FakePages(List.of("{\"sourceCount\":1,\"rows\":[" + row(HEX1) + "]}"));
        FakeSink sink = new FakeSink();
        NativeIdentityEntitlementImportService.Result r =
                new NativeIdentityEntitlementImportService(src, sink, 100, false).importAll();
        assertEquals(1, r.getPersisted());
        assertFalse(r.isSweepRan());
        assertNull(sink.sweptKeep);
    }
}
