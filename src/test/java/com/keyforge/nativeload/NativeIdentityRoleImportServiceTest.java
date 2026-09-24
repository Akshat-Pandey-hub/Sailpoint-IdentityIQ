package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration: page-by-Identity, incomplete-scan guard, and soft-delete sweep for identity-role edges. */
class NativeIdentityRoleImportServiceTest {

    private static final class FakePages implements NativeIdentityRolePageSource {
        private final List<String> pages;
        final List<Integer> starts = new ArrayList<>();
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            starts.add(start);
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"returnedIdentities\":0,\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativeIdentityRoleSink {
        Collection<String> swept;
        @Override public void ensure() { }
        @Override public NativeIdentityRoleRepository.UpsertOutcome upsert(NativeIdentityRoleRecord r) {
            return NativeIdentityRoleRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.swept = keep;
            return new SoftDeleteSweeper.SweepResult("kf_identity_role", keep.size(), 0, 0, false, null);
        }
    }

    private static String edge(String id, String role, String type) {
        return "{\"identityId\":\"" + id + "\",\"roleId\":\"" + role + "\",\"relationshipType\":\"" + type + "\"}";
    }

    @Test
    void oneIdentityManyEdgesSweepsWhenIdentityScanMatches() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeIdentityRoleImportService.Result r = new NativeIdentityRoleImportService(
                new FakePages(List.of("{\"sourceCount\":1,\"returnedIdentities\":1,\"rows\":["
                        + edge("i1", "r1", "ASSIGNED") + "," + edge("i1", "r2", "DETECTED") + "]}")),
                sink, 100, true).importAll();
        assertEquals(2, r.getExtracted());
        assertEquals(1, r.getScannedIdentities());
        assertTrue(r.isSweepRan());
        assertEquals(2, sink.swept.size());
    }

    @Test
    void pagesByIdentityNotByEdges() throws SQLException {
        FakeSink sink = new FakeSink();
        FakePages pages = new FakePages(List.of(
                "{\"sourceCount\":3,\"returnedIdentities\":2,\"rows\":[" + edge("i1", "r1", "ASSIGNED") + "]}",
                "{\"sourceCount\":3,\"returnedIdentities\":1,\"rows\":[" + edge("i3", "r1", "ASSIGNED") + "]}"));
        NativeIdentityRoleImportService.Result r =
                new NativeIdentityRoleImportService(pages, sink, 2, true).importAll();
        assertEquals(3, r.getScannedIdentities());
        assertTrue(r.isSweepRan());
        assertEquals(List.of(0, 2), pages.starts);
    }

    @Test
    void incompleteIdentityScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeIdentityRoleImportService(
                new FakePages(List.of("{\"sourceCount\":3,\"returnedIdentities\":1,\"rows\":["
                        + edge("i1", "r1", "ASSIGNED") + "]}")), sink, 2, true).importAll());
        assertNull(sink.swept, "sweep must not run when not all Identities were scanned");
    }
}
