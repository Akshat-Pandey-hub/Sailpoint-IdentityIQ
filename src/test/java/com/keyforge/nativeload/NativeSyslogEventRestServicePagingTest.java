package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct (window-only) paging of {@code /kfagent/syslog-events} via {@link NativeSyslogEventRestService#fetchPage}.
 * The fake plugin behaves like {@code NativeSyslogEventResource}: rows in a fixed (created, id) order, honours
 * {@code start}/{@code limit} with its MAX_LIMIT clamp, echoes the effective {@code limit}, and reports
 * {@code sourceCount}. Proves an unfiltered page costs only its own plugin call(s), equals the full-scan window,
 * reports the source count, and fails loudly on shrink / error / malformed responses.
 */
class NativeSyslogEventRestServicePagingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NativeSyslogEventRestService svc = new NativeSyslogEventRestService();

    /** Offset-honouring plugin fake; records every (start, limit) call. */
    private static final class FakePlugin implements NativeSyslogEventPageSource {
        final List<int[]> calls = new ArrayList<>();
        final int total;
        final int maxLimit;
        IntUnaryOperator countForCall = c -> -1; // call index -> sourceCount override (-1 = total)
        String forced;                            // a fixed body to return instead (error / malformed)

        FakePlugin(int total, int maxLimit) {
            this.total = total;
            this.maxLimit = maxLimit;
        }

        @Override
        public String fetchPage(int start, int limit) {
            calls.add(new int[]{start, limit});
            if (forced != null) {
                return forced;
            }
            int effective = limit <= 0 ? 200 : Math.min(limit, maxLimit);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = start; i < Math.min(total, start + effective); i++) {
                rows.add(row(i));
            }
            int override = countForCall.applyAsInt(calls.size() - 1);
            Map<String, Object> env = new LinkedHashMap<>();
            env.put("entity", "SyslogEvent");
            env.put("start", start);
            env.put("limit", effective);
            env.put("sourceCount", override >= 0 ? override : total);
            env.put("returned", rows.size());
            env.put("rows", rows);
            try {
                return MAPPER.writeValueAsString(env);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static Map<String, Object> row(int i) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sourceId", String.format("%032x", i + 1));
            r.put("quickKey", String.format("%010d", i + 1));
            r.put("eventLevel", i % 3 == 0 ? "ERROR" : "WARN");
            r.put("server", "host");
            r.put("username", "system");
            r.put("thread", "main");
            r.put("classname", "sailpoint.Cls" + i);
            r.put("lineNumber", Integer.toString(i));
            r.put("message", "m" + i);
            r.put("stacktrace", null);
            r.put("created", String.format("2026-01-01T00:%02d:%02dZ", (i / 60) % 60, i % 60));
            return r;
        }
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add((String) r.get("source_id")));
        return out;
    }

    @Test
    void unfilteredPageReadsOnlyItsWindowWithOnePluginCall() {
        FakePlugin plugin = new FakePlugin(2500, 1000);
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, Map.of(), 1000, 500);

        assertEquals(1, plugin.calls.size(), "one plugin call, no full scan");
        assertEquals(1000, plugin.calls.get(0)[0]);
        assertEquals(500, plugin.calls.get(0)[1]);
        assertEquals(500, page.rows().size());
        assertEquals(String.format("%032x", 1001), page.rows().get(0).get("source_id"));
        assertEquals(2500, page.sourceCount(), "plugin sourceCount is reported");
        assertEquals(11, page.rows().get(0).size(), "same 11-field body as before");
    }

    @Test
    void successivePagesEqualTheFullScanAndEndOnAShortPage() {
        FakePlugin full = new FakePlugin(1234, 1000);
        List<Map<String, Object>> expected = svc.fetch(full, null, null, null);
        assertEquals(1234, expected.size());

        FakePlugin plugin = new FakePlugin(1234, 1000);
        List<Map<String, Object>> collected = new ArrayList<>();
        int start = 0;
        int requests = 0;
        while (true) {
            NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, start, 500);
            requests++;
            collected.addAll(page.rows());
            if (page.rows().size() < 500) {
                break;
            }
            start += 500;
        }
        assertEquals(3, requests, "500 + 500 + 234");
        assertEquals(4, plugin.calls.size(), "one plugin call per page (plus one empty call after the short last page) — never a full scan per page");
        assertEquals(ids(expected), ids(collected), "deterministic: identical rows in identical order");

        NativeSyslogEventRestService.Page past = svc.fetchPage(plugin, null, 5000, 500);
        assertEquals(List.of(), past.rows(), "past the end -> []");
    }

    @Test
    void windowsLargerThanThePluginMaximumAreChunked() {
        FakePlugin plugin = new FakePlugin(2500, 1000);
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, 100, 2100);
        assertEquals(2100, page.rows().size());
        assertEquals(3, plugin.calls.size());
        assertEquals(100, plugin.calls.get(0)[0]);
        assertEquals(1100, plugin.calls.get(1)[0]);
        assertEquals(2100, plugin.calls.get(2)[0]);
        assertEquals(100, plugin.calls.get(2)[1], "last chunk asks only for the remainder");
    }

    @Test
    void aPluginThatClampsBelowTheRequestIsStillReadToTheEndOfTheWindow() {
        FakePlugin plugin = new FakePlugin(900, 300); // plugin max smaller than our chunk
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, 0, 800);
        assertEquals(800, page.rows().size(), "a clamped full page is not mistaken for the end");
        assertEquals(3, plugin.calls.size());
    }

    @Test
    void sourceCountShrinkingInsideAWindowIsFatal() {
        FakePlugin plugin = new FakePlugin(2500, 1000);
        plugin.countForCall = c -> c == 0 ? 2500 : 2400;
        NativeImportException e = assertThrows(NativeImportException.class,
                () -> svc.fetchPage(plugin, null, 0, 1500));
        assertTrue(e.getMessage().contains("shrank"));
    }

    @Test
    void sourceCountGrowthIsToleratedAndTheBaselineIsReported() {
        FakePlugin plugin = new FakePlugin(2500, 1000);
        plugin.countForCall = c -> c == 0 ? 2500 : 2600; // new events appended at the tail
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, 0, 1500);
        assertEquals(1500, page.rows().size());
        assertEquals(2500, page.sourceCount(), "the first plugin call's count is the window's baseline");
    }

    @Test
    void errorEnvelopeAndMalformedBodiesFailLoudly() {
        FakePlugin error = new FakePlugin(10, 1000);
        error.forced = "{\"entity\":\"SyslogEvent\",\"status\":500,\"error\":{\"type\":\"java.lang.IllegalStateException\","
                + "\"message\":\"db down\"}}";
        NativeImportException e = assertThrows(NativeImportException.class, () -> svc.fetchPage(error, null, 0, 50));
        assertTrue(e.getMessage().contains("db down"));

        FakePlugin html = new FakePlugin(10, 1000);
        html.forced = "<html>login</html>";
        assertThrows(NativeImportException.class, () -> svc.fetchPage(html, null, 0, 50));

        FakePlugin noRows = new FakePlugin(10, 1000);
        noRows.forced = "{\"sourceCount\":10}";
        assertThrows(NativeImportException.class, () -> svc.fetchPage(noRows, null, 0, 50));
    }

    @Test
    void filteredRequestStopsReadingOnceTheWindowIsFilled() {
        FakePlugin plugin = new FakePlugin(300_000, 1000);
        NativeSyslogEventRestService.Page filtered =
                svc.fetchPage(plugin, Map.of("event_level", "ERROR"), 10, 5);

        // ERROR rows are i % 3 == 0, so matches 10..14 are rows 30, 33, 36, 39, 42 - all on the first page.
        assertEquals(List.of(String.format("%032x", 31), String.format("%032x", 34), String.format("%032x", 37),
                String.format("%032x", 40), String.format("%032x", 43)), ids(filtered.rows()),
                "filter applied BEFORE start/limit");
        assertEquals(1, plugin.calls.size(), "one 500-row page, not a scan of 300,000");
        assertEquals(0, plugin.calls.get(0)[0]);
        assertEquals(500, plugin.calls.get(0)[1]);
        assertEquals(300_000, filtered.sourceCount(), "the plugin's source count is still reported");
    }

    @Test
    void filterMatchOnALaterPageReadsOnlyUpToThatPage() {
        FakePlugin plugin = new FakePlugin(300_000, 1000);
        String wanted = "sailpoint.Cls2600"; // row 2600 sits on the page starting at 2500
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, Map.of("classname", wanted), 0, 1);

        assertEquals(List.of(String.format("%032x", 2601)), ids(page.rows()));
        assertEquals(6, plugin.calls.size(), "pages 0,500,...,2500 only");
        assertEquals(2500, plugin.calls.get(5)[0]);
    }

    @Test
    void startWithoutLimitNeverReadsTheSkippedRows() {
        FakePlugin plugin = new FakePlugin(2500, 1000);
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, 2000, null);

        assertEquals(500, page.rows().size());
        assertEquals(String.format("%032x", 2001), page.rows().get(0).get("source_id"));
        assertEquals(2000, plugin.calls.get(0)[0], "first plugin call starts at the offset, not at 0");
        assertEquals(2, plugin.calls.size(), "the 500-row page, then one empty call confirming the end");
    }

    @Test
    void sourceCountShrinkingDuringAFilteredScanIsFatal() {
        FakePlugin plugin = new FakePlugin(3000, 1000);
        plugin.countForCall = c -> c == 0 ? 3000 : 2900;
        assertThrows(NativeImportException.class,
                () -> svc.fetchPage(plugin, Map.of("classname", "sailpoint.Cls2999"), 0, 1));
    }

    @Test
    void noParametersStillReturnsTheWholePopulationAndUnknownFiltersAreRejected() {
        FakePlugin all = new FakePlugin(1200, 1000);
        assertEquals(1200, svc.fetchPage(all, null, null, null).rows().size(), "no parameters -> all rows");

        FakePlugin plugin = new FakePlugin(100, 1000);
        assertThrows(IllegalArgumentException.class, () -> svc.fetchPage(plugin, Map.of("syslogid", "x"), 0, 5));
        assertEquals(0, plugin.calls.size());
    }

    @Test
    void zeroLimitReturnsAnEmptyPageWithoutCallingThePlugin() {
        FakePlugin plugin = new FakePlugin(100, 1000);
        NativeSyslogEventRestService.Page page = svc.fetchPage(plugin, null, 0, 0);
        assertEquals(List.of(), page.rows());
        assertEquals(-1, page.sourceCount());
        assertEquals(0, plugin.calls.size());
    }
}
