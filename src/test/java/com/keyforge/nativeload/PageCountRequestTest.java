package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Syslog events and certification archives: a page read never asks the plugin to count the whole population (the
 * count reads every source row, so a start/limit request must cost only its own rows). Only a full read (no limit)
 * asks, once, on its first page, for its completeness check. The fakes behave like the plugin: they count only
 * when asked and report {@code -1} otherwise.
 */
class PageCountRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A plugin fake over {@code total} rows in a fixed order; records whether each call asked for the count. */
    private abstract static class CountingPlugin {
        final List<Boolean> countAsked = new ArrayList<>();
        final int total;
        int reportedTotal = -1; // what the count says when asked (-1 = the real total)

        CountingPlugin(int total) {
            this.total = total;
        }

        String page(int start, int limit, boolean includeCount) {
            countAsked.add(includeCount);
            int effective = limit <= 0 ? 100 : Math.min(limit, 1000);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = start; i < Math.min(total, start + effective); i++) {
                rows.add(row(i));
            }
            Map<String, Object> env = new LinkedHashMap<>();
            env.put("start", start);
            env.put("limit", effective);
            env.put("sourceCount", includeCount ? (reportedTotal >= 0 ? reportedTotal : total) : -1);
            env.put("returned", rows.size());
            env.put("rows", rows);
            try {
                return MAPPER.writeValueAsString(env);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        abstract Map<String, Object> row(int i);
    }

    private static final class SyslogPlugin extends CountingPlugin implements NativeSyslogEventPageSource {
        SyslogPlugin(int total) {
            super(total);
        }

        @Override
        public String fetchPage(int start, int limit) {
            return fetchPage(start, limit, false);
        }

        @Override
        public String fetchPage(int start, int limit, boolean includeCount) {
            return page(start, limit, includeCount);
        }

        @Override
        Map<String, Object> row(int i) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sourceId", String.format("%032x", i + 1));
            r.put("eventLevel", i % 2 == 0 ? "ERROR" : "WARN");
            r.put("message", "m" + i);
            r.put("created", String.format("2026-01-01T00:%02d:%02dZ", (i / 60) % 60, i % 60));
            return r;
        }
    }

    private static final class ArchivePlugin extends CountingPlugin implements NativeCertificationArchivePageSource {
        ArchivePlugin(int total) {
            super(total);
        }

        @Override
        public String fetchPage(int start, int limit) {
            return fetchPage(start, limit, false);
        }

        @Override
        public String fetchPage(int start, int limit, boolean includeCount) {
            return page(start, limit, includeCount);
        }

        @Override
        Map<String, Object> row(int i) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sourceId", String.format("%032x", i + 1));
            r.put("name", i % 2 == 0 ? "even" : "odd");
            r.put("created", "2026-10-02T15:23:52.185Z");
            return r;
        }
    }

    private static boolean anyAsked(CountingPlugin p) {
        return p.countAsked.contains(Boolean.TRUE);
    }

    // ---- syslog events

    @Test
    void syslogSmallPageDeepInTheTableIsOneUncountedCall() {
        SyslogPlugin plugin = new SyslogPlugin(3000);
        NativeSyslogEventRestService.Page page =
                new NativeSyslogEventRestService().fetchPage(plugin, Map.of(), 2000, 2);
        assertEquals(List.of(Boolean.FALSE), plugin.countAsked);
        assertEquals(2, page.rows().size());
        assertEquals(String.format("%032x", 2001), page.rows().get(0).get("source_id"));
        assertEquals(-1, page.sourceCount(), "no count, so no X-KF-Source-Count header");
    }

    @Test
    void syslogMultiCallWindowAndFilteredReadNeverCount() {
        SyslogPlugin window = new SyslogPlugin(2500);
        assertEquals(2400, new NativeSyslogEventRestService().fetchPage(window, Map.of(), 50, 2400).rows().size());
        assertTrue(window.countAsked.size() > 1);
        assertFalse(anyAsked(window));

        SyslogPlugin filtered = new SyslogPlugin(2500);
        List<Map<String, Object>> errors =
                new NativeSyslogEventRestService().fetch(filtered, Map.of("event_level", "ERROR"), 0, 10);
        assertEquals(10, errors.size());
        assertFalse(anyAsked(filtered));
    }

    @Test
    void syslogFullReadCountsOnlyOnItsFirstPage() {
        SyslogPlugin plugin = new SyslogPlugin(1234);
        NativeSyslogEventRestService.Page page = new NativeSyslogEventRestService().fetchPage(plugin, null, null, null);
        assertEquals(1234, page.rows().size());
        assertEquals(1234, page.sourceCount());
        assertEquals(Boolean.TRUE, plugin.countAsked.get(0));
        assertFalse(plugin.countAsked.subList(1, plugin.countAsked.size()).contains(Boolean.TRUE));
        assertTrue(plugin.countAsked.size() > 1);
    }

    @Test
    void syslogFullReadStillFailsWhenItReadsFewerRowsThanTheFirstPageCounted() {
        SyslogPlugin plugin = new SyslogPlugin(700);
        plugin.reportedTotal = 900; // rows vanished after the count
        assertThrows(NativeImportException.class,
                () -> new NativeSyslogEventRestService().fetchPage(plugin, null, null, null));
    }

    // ---- certification archives

    @Test
    void archiveSmallPageDeepInTheTableIsOneUncountedCall() {
        ArchivePlugin plugin = new ArchivePlugin(3000);
        List<Map<String, Object>> rows = new NativeCertificationArchiveRestService().fetch(plugin, null, 2000, 1);
        assertEquals(List.of(Boolean.FALSE), plugin.countAsked);
        assertEquals(1, rows.size());
        assertEquals(String.format("%032x", 2001), rows.get(0).get("source_id"));
    }

    @Test
    void archiveMultiCallWindowAndFilteredReadNeverCount() {
        ArchivePlugin window = new ArchivePlugin(2500);
        assertEquals(2400, new NativeCertificationArchiveRestService().fetch(window, null, 50, 2400).size());
        assertTrue(window.countAsked.size() > 1);
        assertFalse(anyAsked(window));

        ArchivePlugin filtered = new ArchivePlugin(2500);
        assertEquals(10, new NativeCertificationArchiveRestService().fetch(filtered, Map.of("name", "odd"), 0, 10).size());
        assertFalse(anyAsked(filtered));
    }

    @Test
    void archiveFullReadCountsOnlyOnItsFirstPageAndKeepsTheCompletenessCheck() {
        ArchivePlugin plugin = new ArchivePlugin(1234);
        assertEquals(1234, new NativeCertificationArchiveRestService().fetch(plugin, null, null, null).size());
        assertEquals(Boolean.TRUE, plugin.countAsked.get(0));
        assertFalse(plugin.countAsked.subList(1, plugin.countAsked.size()).contains(Boolean.TRUE));

        ArchivePlugin incomplete = new ArchivePlugin(700);
        incomplete.reportedTotal = 900;
        assertThrows(NativeImportException.class,
                () -> new NativeCertificationArchiveRestService().fetch(incomplete, null, null, null));
    }
}
