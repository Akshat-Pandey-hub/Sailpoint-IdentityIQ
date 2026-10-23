package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded reads for {@code /kfagent/entitlements}. The fake plugin behaves like {@code NativeManagedAttributeResource}:
 * a fixed id-ordered population, {@code firstRow=start}, {@code resultLimit=min(limit, MAX_LIMIT)} (DEFAULT_LIMIT when
 * {@code limit<=0}), and it echoes the effective {@code limit}. Every call is recorded so the tests can prove how much
 * of the population a request actually read. Expected rows always come from the old behaviour (whole population,
 * then filter, then window) so the response contract is unchanged.
 */
class NativeEntitlementRestServicePagingTest {

    private static final int MAX_LIMIT = 1000;
    private static final int DEFAULT_LIMIT = 100;

    private final NativeEntitlementRestService svc = new NativeEntitlementRestService();

    /** Population row i: id "e%05d", application "App-(i%3)", plus the given "special" rows on application "Target". */
    private static final class FakePlugin implements NativeManagedAttributePageSource {
        final int size;
        final List<Integer> special;
        final List<int[]> calls = new ArrayList<>();
        int failAtStart = -1;

        FakePlugin(int size, Integer... special) {
            this.size = size;
            this.special = List.of(special);
        }

        String app(int i) {
            return special.contains(i) ? "Target" : "App-" + (i % 3);
        }

        @Override
        public String fetchPage(int start, int limit) {
            calls.add(new int[]{start, limit});
            if (start == failAtStart) {
                return "{\"entity\":\"ManagedAttribute\",\"status\":500,"
                        + "\"error\":{\"type\":\"java.lang.RuntimeException\",\"message\":\"boom\"}}";
            }
            int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
            int from = Math.max(0, start);
            int to = Math.min(size, from + safeLimit);
            StringBuilder rows = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (rows.length() > 0) {
                    rows.append(',');
                }
                rows.append("{\"sourceId\":\"").append(id(i)).append("\",\"value\":\"v").append(i)
                        .append("\",\"applicationName\":\"").append(app(i)).append("\",\"type\":\"group\"}");
            }
            return "{\"entity\":\"ManagedAttribute\",\"start\":" + from + ",\"limit\":" + safeLimit
                    + ",\"returned\":" + Math.max(0, to - from) + ",\"rows\":[" + rows + "]}";
        }

        int rowsRead() {
            int n = 0;
            for (int[] c : calls) {
                int safeLimit = c[1] <= 0 ? DEFAULT_LIMIT : Math.min(c[1], MAX_LIMIT);
                n += Math.max(0, Math.min(size, c[0] + safeLimit) - c[0]);
            }
            return n;
        }

        /** The old behaviour: whole population, filter, then the start/limit window. */
        List<String> expected(Predicate<Integer> match, int start, Integer limit) {
            List<String> all = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (match.test(i)) {
                    all.add(id(i));
                }
            }
            int from = Math.min(start, all.size());
            int to = limit == null ? all.size() : Math.min(all.size(), from + limit);
            return all.subList(from, to);
        }
    }

    private static String id(int i) {
        return String.format("e%05d", i);
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add((String) r.get("source_id"));
        }
        return out;
    }

    private static Map<String, String> f(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    // --- unfiltered start/limit: read only the window --------------------------------------------------------

    @Test
    void smallUnfilteredWindowReadsOnlyThatWindow() {
        FakePlugin plugin = new FakePlugin(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 4_000, 2);

        assertEquals(plugin.expected(i -> true, 4_000, 2), ids(rows));
        assertEquals(1, plugin.calls.size(), "one plugin call, not a scan of 10,000");
        assertEquals(4_000, plugin.calls.get(0)[0]);
        assertEquals(2, plugin.calls.get(0)[1]);
        assertEquals(2, plugin.rowsRead());
        assertEquals(43, rows.get(0).size(), "same 43-field body as before");
    }

    @Test
    void windowLargerThanPluginMaximumIsChunked() {
        FakePlugin plugin = new FakePlugin(3_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 0, 2_500);

        assertEquals(plugin.expected(i -> true, 0, 2_500), ids(rows));
        assertEquals(3, plugin.calls.size(), "1000 + 1000 + 500");
        assertEquals(List.of(0, 1_000, 2_000), List.of(plugin.calls.get(0)[0], plugin.calls.get(1)[0], plugin.calls.get(2)[0]));
        assertEquals(500, plugin.calls.get(2)[1]);
    }

    @Test
    void windowAtAndPastTheEnd() {
        FakePlugin plugin = new FakePlugin(3_000);
        assertEquals(plugin.expected(i -> true, 2_990, 50), ids(svc.fetch(plugin, null, 2_990, 50)), "short last window");
        assertEquals(List.of(), ids(svc.fetch(plugin, null, 5_000, 10)), "past the end -> []");
        assertEquals(List.of(), ids(svc.fetch(plugin, null, 0, 0)), "limit=0 -> []");
        assertEquals(2, plugin.calls.size(), "limit=0 makes no plugin call");
    }

    @Test
    void successivePagesEqualTheFullPopulationInOrder() {
        FakePlugin plugin = new FakePlugin(1_234);
        List<String> collected = new ArrayList<>();
        int requests = 0;
        for (int start = 0; ; start += 500) {
            List<Map<String, Object>> page = svc.fetch(plugin, null, start, 500);
            requests++;
            collected.addAll(ids(page));
            if (page.size() < 500) {
                break;
            }
        }
        assertEquals(plugin.expected(i -> true, 0, null), collected, "identical rows in identical order");
        assertEquals(3, requests, "500 + 500 + 234");
        assertEquals(3, plugin.calls.size(), "one plugin call per page, never a full scan per page");
        assertEquals(1_234, plugin.rowsRead(), "every row read exactly once across the pages");
    }

    @Test
    void limitHundredReadsOnlyHundredOfTenThousand() {
        FakePlugin plugin = new FakePlugin(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, null, 100);

        assertEquals(plugin.expected(i -> true, 0, 100), ids(rows));
        assertEquals(1, plugin.calls.size(), "one plugin call");
        assertEquals(0, plugin.calls.get(0)[0], "plugin start=0");
        assertEquals(100, plugin.calls.get(0)[1], "plugin limit=100");
        assertEquals(100, plugin.rowsRead(), "100 rows read from IIQ, not 10,000");
    }

    @Test
    void startOneLimitTwoSkipsTheFirstAndReturnsExactlyTwo() {
        FakePlugin plugin = new FakePlugin(10);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 1, 2);

        assertEquals(List.of(id(1), id(2)), ids(rows), "exact records, exact order");
        assertEquals(1, plugin.calls.size());
        assertEquals(1, plugin.calls.get(0)[0], "plugin firstRow=1");
        assertEquals(2, plugin.calls.get(0)[1], "plugin resultLimit=2");
    }

    // --- filtered: correct across pages, stop when the window is full ---------------------------------------

    @Test
    void sourceIdReturnsOnlyThatEntitlementAndStopsAtIt() {
        FakePlugin plugin = new FakePlugin(10_000);
        List<Map<String, Object>> rows = svc.fetch(plugin, f("source_id", id(1_234)), null, null);

        assertEquals(List.of(id(1_234)), ids(rows));
        assertEquals(43, rows.get(0).size(), "same 43-field body");
        assertEquals(3, plugin.calls.size(), "pages 0,500,1000 only: the primary key is unique, so the scan stops");
        assertEquals(1_500, plugin.rowsRead());
    }

    @Test
    void sourceIdThatDoesNotExistOrIsSkippedReturnsEmpty() {
        FakePlugin plugin = new FakePlugin(1_200);
        assertEquals(List.of(), ids(svc.fetch(plugin, f("source_id", "missing"), null, null)));
        int callsForMissing = plugin.calls.size();
        assertEquals(List.of(), ids(svc.fetch(plugin, f("source_id", id(5)), 1, 2)), "start=1 skips the only match");
        assertEquals(callsForMissing, plugin.calls.size(), "start>0 with a unique id needs no plugin call");
        assertEquals(List.of(id(5)), ids(svc.fetch(plugin, f("source_id", id(5)), 0, 10)));
    }

    @Test
    void filterFindsAMatchOnALaterPageAndStopsThere() {
        FakePlugin plugin = new FakePlugin(10_000, 7_777);
        List<Map<String, Object>> rows = svc.fetch(plugin, f("application_name", "Target"), 0, 1);

        assertEquals(List.of(id(7_777)), ids(rows));
        assertEquals(16, plugin.calls.size(), "pages 0..7500 read in 500s; stops on the page holding the match");
        assertTrue(plugin.rowsRead() < 10_000, "did not read past the page with the match");
    }

    @Test
    void filterWithStartAndLimitAcrossPageBoundaries() {
        Integer[] special = {120, 499, 500, 501, 1_499, 1_500, 2_750, 2_999};
        FakePlugin plugin = new FakePlugin(3_000, special);
        Predicate<Integer> isTarget = i -> plugin.special.contains(i);

        assertEquals(plugin.expected(isTarget, 2, 3), ids(svc.fetch(plugin, f("application_name", "Target"), 2, 3)));
        assertEquals(plugin.expected(isTarget, 6, 10), ids(svc.fetch(plugin, f("application_name", "Target"), 6, 10)),
                "window running past the last match");
        assertEquals(plugin.expected(isTarget, 0, null), ids(svc.fetch(plugin, f("application_name", "Target"), 0, null)),
                "no limit -> every match, in order");
        assertEquals(plugin.expected(isTarget, 20, 5), ids(svc.fetch(plugin, f("application_name", "Target"), 20, 5)),
                "start beyond the matches -> []");
    }

    @Test
    void filterWithoutLimitReadsEverythingButReturnsOnlyMatches() {
        FakePlugin plugin = new FakePlugin(2_100);
        List<Map<String, Object>> rows = svc.fetch(plugin, f("application_name", "App-1"), null, null);

        assertEquals(plugin.expected(i -> i % 3 == 1, 0, null), ids(rows));
        assertEquals(2_100, plugin.rowsRead(), "a match could be anywhere, so every page is read once");
    }

    // --- snapshot unchanged, errors surface -----------------------------------------------------------------

    @Test
    void noFiltersNoLimitStillReturnsTheWholePopulation() {
        FakePlugin plugin = new FakePlugin(1_234);
        assertEquals(plugin.expected(i -> true, 0, null), ids(svc.fetch(plugin, null, null, null)));
        assertEquals(plugin.expected(i -> true, 1_000, null), ids(svc.fetch(plugin, null, 1_000, null)),
                "start without limit -> the rest of the population");
    }

    @Test
    void startWithoutLimitNeverReadsTheSkippedRows() {
        FakePlugin plugin = new FakePlugin(2_500);
        List<Map<String, Object>> rows = svc.fetch(plugin, null, 2_000, null);

        assertEquals(plugin.expected(i -> true, 2_000, null), ids(rows));
        assertEquals(2_000, plugin.calls.get(0)[0], "first plugin call starts at the offset, not at 0");
        assertEquals(500, plugin.rowsRead(), "only rows 2000..2499 are read");
    }

    @Test
    void anUpstreamErrorIsNeverTurnedIntoAShortSuccessfulPage() {
        FakePlugin windowed = new FakePlugin(3_000);
        windowed.failAtStart = 1_000;
        assertThrows(NativeImportException.class, () -> svc.fetch(windowed, null, 0, 2_000));

        FakePlugin filtered = new FakePlugin(3_000, 2_900);
        filtered.failAtStart = 1_000;
        assertThrows(NativeImportException.class, () -> svc.fetch(filtered, f("application_name", "Target"), 0, 1));
    }

    @Test
    void unknownFilterIsStillRejectedBeforeAnyPluginCall() {
        FakePlugin plugin = new FakePlugin(100);
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(plugin, f("no_such_field", "x"), 0, 1));
        assertEquals(0, plugin.calls.size());
    }
}
