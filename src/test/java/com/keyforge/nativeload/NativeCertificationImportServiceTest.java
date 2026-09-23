package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests for {@link NativeCertificationImportService} (current-state guard + sweep). */
class NativeCertificationImportServiceTest {

    private static final class FakePages implements NativeCertificationPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeCertificationSink {
        Collection<String> sweptKeep;
        @Override public void ensure() { }
        @Override public NativeCertificationRepository.UpsertOutcome upsert(NativeCertificationRecord r) {
            return NativeCertificationRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            return new SoftDeleteSweeper.SweepResult("kf_certification", keep.size(), 0, 0, false, null);
        }
    }

    @Test
    void persistsAndSweepsWhenScanMatches() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeCertificationImportService.Result r = new NativeCertificationImportService(
                new FakePages("{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"c1\",\"name\":\"Q1\"}]}"),
                sink, 100, true).importAll();
        assertEquals(1, r.getPersisted());
        assertTrue(r.isSweepRan());
        assertEquals(1, sink.sweptKeep.size());
    }

    @Test
    void incompleteScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeCertificationImportService(
                new FakePages("{\"sourceCount\":7,\"rows\":[{\"sourceId\":\"c1\",\"name\":\"Q1\"}]}"),
                sink, 100, true).importAll());
        assertNull(sink.sweptKeep);
    }
}
