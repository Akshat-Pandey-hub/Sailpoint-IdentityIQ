package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Stage-1 incremental (event-shaped, append-only datasets): each KF Agent → plugin client forwards an optional
 * {@code modifiedAfter} to the plugin, and sends exactly the same request as before when it is absent or blank.
 * Uses a recording {@link IiqSessionClient} (no network, no IIQ).
 */
class EventIncrementalClientTest {

    private static final String BOUND = "2026-10-12T10:00:00Z";

    /** Records every plugin GET (path + params); never opens a connection. */
    private static final class RecordingSession extends IiqSessionClient {
        final List<String> paths = new ArrayList<>();
        final List<Map<String, String>> params = new ArrayList<>();

        RecordingSession() {
            super("http://localhost:1/identityiq", "u", "p", null); // no real HttpClient: every network call is overridden
        }

        @Override
        public void warmCsrfToken() {
            // no session needed
        }

        @Override
        public String get(String path, Map<String, String> queryParams) {
            paths.add(path);
            params.add(new LinkedHashMap<>(queryParams));
            return "{}";
        }
    }

    private static void assertForwardsBound(Function<RecordingSession, Runnable> withBound,
                                            Function<RecordingSession, Runnable> withoutBound) {
        RecordingSession s = new RecordingSession();
        withBound.apply(s).run();
        assertEquals(BOUND, s.params.get(0).get("modifiedAfter"));
        assertEquals("0", s.params.get(0).get("start"));
        assertEquals("50", s.params.get(0).get("limit"));

        RecordingSession plain = new RecordingSession();
        withoutBound.apply(plain).run();
        assertFalse(plain.params.get(0).containsKey("modifiedAfter"), "no bound → unchanged request");
        assertEquals(Map.of("start", "0", "limit", "50"), plain.params.get(0));
    }

    @Test
    void auditEventClient() {
        assertForwardsBound(
                s -> () -> new NativeAuditEventClient(s).withModifiedAfter(BOUND).fetchPage(0, 50),
                s -> () -> new NativeAuditEventClient(s).fetchPage(0, 50));
    }

    @Test
    void syslogEventClient() {
        assertForwardsBound(
                s -> () -> new NativeSyslogEventClient(s).withModifiedAfter(BOUND).fetchPage(0, 50),
                s -> () -> new NativeSyslogEventClient(s).withModifiedAfter("  ").fetchPage(0, 50));
    }

    @Test
    void syslogAndCertificationArchiveClientsAskForTheCountOnlyWhenTold() {
        for (Function<RecordingSession, Runnable[]> calls : List.<Function<RecordingSession, Runnable[]>>of(
                s -> new Runnable[]{() -> new NativeSyslogEventClient(s).fetchPage(0, 50),
                        () -> new NativeSyslogEventClient(s).fetchPage(0, 50, false),
                        () -> new NativeSyslogEventClient(s).fetchPage(0, 50, true)},
                s -> new Runnable[]{() -> new NativeCertificationArchiveClient(s).fetchPage(0, 50),
                        () -> new NativeCertificationArchiveClient(s).fetchPage(0, 50, false),
                        () -> new NativeCertificationArchiveClient(s).fetchPage(0, 50, true)})) {
            RecordingSession s = new RecordingSession();
            for (Runnable call : calls.apply(s)) {
                call.run();
            }
            assertEquals(Map.of("start", "0", "limit", "50"), s.params.get(0), "a page call never asks for the count");
            assertEquals(Map.of("start", "0", "limit", "50"), s.params.get(1));
            assertEquals(Map.of("start", "0", "limit", "50", "includeCount", "true"), s.params.get(2));
        }
    }

    @Test
    void workItemArchiveClient() {
        assertForwardsBound(
                s -> () -> new NativeWorkItemArchiveClient(s).withModifiedAfter(BOUND).fetchPage(0, 50),
                s -> () -> new NativeWorkItemArchiveClient(s).withModifiedAfter(null).fetchPage(0, 50));
    }

    @Test
    void certificationArchiveClient() {
        assertForwardsBound(
                s -> () -> new NativeCertificationArchiveClient(s).withModifiedAfter(BOUND).fetchPage(0, 50),
                s -> () -> new NativeCertificationArchiveClient(s).fetchPage(0, 50));
    }

    @Test
    void accessHistoryClientForwardsTheBoundOnAllFourPaths() {
        for (String path : List.of(NativeAccessHistoryClient.ENTITLEMENT_CAPTURES_PATH,
                NativeAccessHistoryClient.IDENTITY_EVENTS_PATH, NativeAccessHistoryClient.ROLE_EVENTS_PATH,
                NativeAccessHistoryClient.CERTIFICATIONS_PATH)) {
            RecordingSession s = new RecordingSession();
            new NativeAccessHistoryClient(s).withModifiedAfter(BOUND).fetchPage(path, 0, 50);
            assertEquals(path, s.paths.get(0));
            assertEquals(BOUND, s.params.get(0).get("modifiedAfter"));

            RecordingSession plain = new RecordingSession();
            new NativeAccessHistoryClient(plain).fetchPage(path, 0, 50);
            assertEquals(Map.of("start", "0", "limit", "50"), plain.params.get(0));
        }
    }
}
