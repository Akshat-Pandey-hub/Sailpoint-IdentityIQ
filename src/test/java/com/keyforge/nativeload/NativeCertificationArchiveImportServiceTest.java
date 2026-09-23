package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Append-only orchestration for {@link NativeCertificationArchiveImportService}: dedup + incomplete guard. */
class NativeCertificationArchiveImportServiceTest {

    private static final class FakePages implements NativeCertificationArchivePageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeCertificationArchiveSink {
        final Set<String> seen = new HashSet<>();
        @Override public void ensure() { }
        @Override public NativeCertificationArchiveRepository.AppendOutcome append(NativeCertificationArchiveRecord r) {
            return seen.add(r.sourceId)
                    ? NativeCertificationArchiveRepository.AppendOutcome.INSERTED
                    : NativeCertificationArchiveRepository.AppendOutcome.SKIPPED;
        }
    }

    @Test
    void rerunDedupsDeterministically() throws SQLException {
        String page = "{\"sourceCount\":2,\"rows\":[{\"sourceId\":\"a1\"},{\"sourceId\":\"a2\"}]}";
        FakeSink sink = new FakeSink();
        NativeCertificationArchiveImportService.Result r1 =
                new NativeCertificationArchiveImportService(new FakePages(page), sink, 100).importAll();
        assertEquals(2, r1.getInserted());
        // second run: same archives already present -> all skipped, none inserted, no duplicate
        NativeCertificationArchiveImportService.Result r2 =
                new NativeCertificationArchiveImportService(new FakePages(page), sink, 100).importAll();
        assertEquals(0, r2.getInserted());
        assertEquals(2, r2.getSkipped());
    }

    @Test
    void incompleteScanThrows() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeCertificationArchiveImportService(
                new FakePages("{\"sourceCount\":5,\"rows\":[{\"sourceId\":\"a1\"}]}"), sink, 100).importAll());
    }
}
