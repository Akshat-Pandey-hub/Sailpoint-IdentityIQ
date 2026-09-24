package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration: upsert + incomplete-scan guard + soft-delete sweep for native WorkItem. */
class NativeWorkItemImportServiceTest {

    private static final String HEX1 = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX2 = "1a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final class FakePages implements NativeWorkItemPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeWorkItemSink {
        Collection<String> swept;
        @Override public void ensure() { }
        @Override public NativeWorkItemRepository.UpsertOutcome upsert(NativeWorkItemRecord r) {
            return NativeWorkItemRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.swept = keep;
            return new SoftDeleteSweeper.SweepResult("kf_workitem", keep.size(), 0, 0, false, null);
        }
    }

    @Test
    void persistsAndSweepsWhenScanMatches() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeWorkItemImportService.Result r = new NativeWorkItemImportService(
                new FakePages("{\"sourceCount\":2,\"rows\":[{\"sourceId\":\"" + HEX1 + "\"},{\"sourceId\":\"" + HEX2 + "\"}]}"),
                sink, 100, true).importAll();
        assertEquals(2, r.getExtracted());
        assertEquals(2, r.getInserted());
        assertTrue(r.isSweepRan());
        assertEquals(2, sink.swept.size());
    }

    @Test
    void incompleteScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeWorkItemImportService(
                new FakePages("{\"sourceCount\":9,\"rows\":[{\"sourceId\":\"" + HEX1 + "\"}]}"),
                sink, 100, true).importAll());
        assertNull(sink.swept, "sweep must not run on an incomplete scan");
    }
}
