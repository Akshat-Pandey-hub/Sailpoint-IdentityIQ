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

/** Orchestration tests for {@link NativeCertificationItemImportService} (current-state guard + sweep). */
class NativeCertificationItemImportServiceTest {

    private static final class FakePages implements NativeCertificationItemPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeCertificationItemSink {
        Collection<String> sweptKeep;
        @Override public void ensure() { }
        @Override public NativeCertificationItemRepository.UpsertOutcome upsert(NativeCertificationItemRecord r) {
            return NativeCertificationItemRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            return new SoftDeleteSweeper.SweepResult("kf_certification_item", keep.size(), 0, 0, false, null);
        }
    }

    @Test
    void persistsAndSweepsWhenScanMatches() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeCertificationItemImportService.Result r = new NativeCertificationItemImportService(
                new FakePages("{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"i1\",\"identity\":\"a\"}]}"),
                sink, 100, true).importAll();
        assertEquals(1, r.getExtracted());
        assertEquals(1, r.getInserted());
        assertTrue(r.isSweepRan());
        assertEquals(1, sink.sweptKeep.size());
    }

    @Test
    void incompleteScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeCertificationItemImportService(
                new FakePages("{\"sourceCount\":9,\"rows\":[{\"sourceId\":\"i1\",\"identity\":\"a\"}]}"),
                sink, 100, true).importAll());
        assertNull(sink.sweptKeep, "sweep must not run on an incomplete scan");
    }
}
