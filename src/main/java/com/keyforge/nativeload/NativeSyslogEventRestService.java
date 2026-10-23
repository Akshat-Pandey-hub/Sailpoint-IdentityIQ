package com.keyforge.nativeload;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native SyslogEvents ({@code sailpoint.object.SyslogEvent} →
 * {@code kf_syslog_event}). SyslogEvent is IIQ's own immutable operational/diagnostic log (server-side
 * logging events), NOT identity-governance data — distinct from AuditEvent. Reuses the EXISTING native
 * extraction verbatim: the same {@link NativeSyslogEventClient} page source + {@link
 * NativeSyslogEventImportService} the DB path uses, with a non-JDBC {@link CollectingSink} that collects
 * records in memory (DB bypass). No existing extraction or DB code is modified; no PostgreSQL is touched
 * on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeSyslogEventRecord} + {@link NativeSyslogEventParser}
 * + {@code kf_syslog_event}'s append binding): 11 SailPoint-facing fields, all scalar text (no structured
 * jsonb). {@code created_at} is an ISO-8601 timestamp; every other field is text. {@code classname} is the
 * Java class that emitted the log line (pairs with {@code line_number} for the source location). All 11
 * fields are filterable. Excluded as technical: the KeyForge {@code syslogid} PK, {@code record_hash} and
 * the lineage envelope ({@code source_system}/{@code source_interface}/{@code source_object_type}/
 * {@code extraction_run_id}/{@code extracted_at}). SyslogEvent is append-only (no soft-delete columns). No
 * native {@code modifiedAfter} (the client has no server-side modified filter → full-scan only).
 */
public final class NativeSyslogEventRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final NativeSyslogEventParser parser = new NativeSyslogEventParser();

    /** Scalar response field name -> value extractor (all 11 scalar, all filterable). */
    private static final Map<String, Function<NativeSyslogEventRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * HTTP response header carrying the source population size (IIQ {@code countObjects(SyslogEvent)}) that the
     * plugin reported for the rows in this response. The body stays a plain JSON array; clients that ignore the
     * header are unaffected. A paging client uses it as the completeness baseline: the first page's value is the
     * population the scan must cover, and a later page reporting FEWER events means events were removed mid-scan
     * (the offset window may have shifted, so rows could have been skipped).
     */
    public static final String SOURCE_COUNT_HEADER = "X-KF-Source-Count";

    /** The plugin's per-call maximum ({@code NativeSyslogEventResource.MAX_LIMIT}); larger windows are chunked. */
    static final int PLUGIN_MAX_PAGE = 1000;

    /** One KF Agent response: the serialized rows plus the source count to report ({@code -1} = unknown). */
    public static final class Page {
        private final List<Map<String, Object>> rows;
        private final int sourceCount;

        Page(List<Map<String, Object>> rows, int sourceCount) {
            this.rows = rows;
            this.sourceCount = sourceCount;
        }

        public List<Map<String, Object>> rows() { return rows; }
        public int sourceCount() { return sourceCount; }
    }

    /**
     * Serves one {@code /kfagent/syslog-events} request without re-scanning the whole population when it does not
     * have to. An UNFILTERED request with a {@code limit} maps directly onto the plugin's own deterministic paging
     * ({@code ORDER BY created, id}; {@code firstRow=start}, {@code resultLimit<=}{@value #PLUGIN_MAX_PAGE}): it reads
     * only the requested window, so successive pages cost one plugin call each instead of a full scan each. The
     * window is the same slice of the same ordered population the full scan would return, so the response body is
     * unchanged. A filtered request (filters must apply BEFORE start/limit) or one without a {@code limit} ("all")
     * keeps the existing full scan via {@link #fetch}.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     * @throws NativeImportException    on a plugin error envelope, a malformed response, or a source count that
     *                                  shrank between the plugin calls of one window
     */
    public Page fetchPage(NativeSyslogEventPageSource source,
                          Map<String, String> filters, Integer start, Integer limit) {
        validateFilters(filters);
        boolean filtered = filters != null && !filters.isEmpty();
        boolean windowed = limit != null && limit >= 0;
        int first = (start == null || start < 0) ? 0 : start;
        if (!filtered && (windowed || first > 0)) {
            // start without limit reads from the offset to the end; the skipped rows are never fetched.
            return directWindow(source, first, windowed ? limit : Integer.MAX_VALUE);
        }
        if (filtered) {
            return filteredScan(source, filters, first, windowed ? limit : null);
        }
        int[] baseline = {-1};
        List<Map<String, Object>> rows = fullScan(source, filters, start, limit, baseline);
        return new Page(rows, baseline[0]);
    }

    /**
     * Filters must apply BEFORE start/limit, so pages are read in (created, id) order from the beginning, but only
     * matching records are kept and reading stops once {@code start + limit} matches are found.
     */
    private Page filteredScan(NativeSyslogEventPageSource source, Map<String, String> filters, int from,
                              Integer limit) {
        CountGuard guard = new CountGuard();
        KfAgentPagedReader<NativeSyslogEventRecord> reader = new KfAgentPagedReader<>(
                (s, l) -> guard.check(source.fetchPage(s, l)), parser::parse, PLUGIN_MAX_PAGE, INTERNAL_PAGE_SIZE);
        List<Map<String, Object>> out = new ArrayList<>();
        for (NativeSyslogEventRecord r : reader.filtered(r -> matches(r, filters), from, limit)) {
            out.add(toJson(r));
        }
        return new Page(out, guard.baseline);
    }

    /** Tracks the plugin-reported source count across the calls of one request; a shrinking count is fatal. */
    private final class CountGuard {
        int baseline = -1;
        int highWater = -1;

        String check(String json) {
            int count = parser.sourceCount(json);
            if (count >= 0) {
                if (baseline < 0) {
                    baseline = count;
                }
                if (highWater >= 0 && count < highWater) {
                    throw new NativeImportException("SyslogEvent source count shrank during paginated extraction: "
                            + highWater + " -> " + count + " (events removed mid-scan; aborting to avoid a gap)");
                }
                highWater = Math.max(highWater, count);
            }
            return json;
        }
    }

    /** Reads exactly the window [from, from+limit) from the plugin, chunked by its maximum page size. */
    private Page directWindow(NativeSyslogEventPageSource source, int from, int limit) {
        List<Map<String, Object>> out = new ArrayList<>(Math.min(limit, PLUGIN_MAX_PAGE));
        CountGuard guard = new CountGuard();
        int offset = from;
        int remaining = limit;
        while (remaining > 0) {
            int chunk = Math.min(remaining, PLUGIN_MAX_PAGE);
            String json = guard.check(source.fetchPage(offset, chunk));
            List<NativeSyslogEventRecord> page = parser.parse(json); // throws on error envelope / malformed body
            for (NativeSyslogEventRecord r : page) {
                out.add(toJson(r));
            }
            int effective = parser.pageLimit(json);
            if (page.isEmpty() || page.size() < (effective > 0 ? Math.min(effective, chunk) : chunk)) {
                break; // end of the population
            }
            offset += page.size();
            remaining -= page.size();
        }
        return new Page(out, guard.baseline);
    }

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeSyslogEventPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        validateFilters(filters);
        return fullScan(source, filters, start, limit, null);
    }

    private static void validateFilters(Map<String, String> filters) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }
    }

    private List<Map<String, Object>> fullScan(NativeSyslogEventPageSource source, Map<String, String> filters,
                                               Integer start, Integer limit, int[] baselineOut) {
        List<NativeSyslogEventRecord> all = collectAll(source, baselineOut);

        List<NativeSyslogEventRecord> matched = new ArrayList<>();
        for (NativeSyslogEventRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeSyslogEventRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeSyslogEventRecord r, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            String actual = SCALARS.get(f.getKey()).apply(r);
            if (actual == null || !actual.equals(f.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** Walks the native source to completion via the EXISTING import; collecting sink = no DB. */
    private List<NativeSyslogEventRecord> collectAll(NativeSyslogEventPageSource source, int[] baselineOut) {
        CollectingSink sink = new CollectingSink();
        try {
            // Append-only import (no sweep); the collecting sink persists nothing — no PostgreSQL is touched.
            NativeSyslogEventImportService.Result result =
                    new NativeSyslogEventImportService(source, sink, INTERNAL_PAGE_SIZE).importAll();
            if (baselineOut != null) {
                baselineOut[0] = result.getSourceCount();
            }
        } catch (SQLException e) {
            throw new NativeImportException("native syslog-event extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_syslog_event order). */
    private Map<String, Object> toJson(NativeSyslogEventRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("quick_key", r.quickKey);
        m.put("event_level", r.eventLevel);
        m.put("server", r.server);
        m.put("username", r.username);
        m.put("thread", r.thread);
        m.put("classname", r.classname);
        m.put("line_number", r.lineNumber);
        m.put("message", r.message);
        m.put("stacktrace", r.stacktrace);
        m.put("created_at", iso(r.created));
        return m;
    }

    private static Map<String, Function<NativeSyslogEventRecord, String>> buildScalars() {
        Map<String, Function<NativeSyslogEventRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("quick_key", r -> r.quickKey);
        m.put("event_level", r -> r.eventLevel);
        m.put("server", r -> r.server);
        m.put("username", r -> r.username);
        m.put("thread", r -> r.thread);
        m.put("classname", r -> r.classname);
        m.put("line_number", r -> r.lineNumber);
        m.put("message", r -> r.message);
        m.put("stacktrace", r -> r.stacktrace);
        m.put("created_at", r -> iso(r.created));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    /** In-memory append-only sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeSyslogEventSink {
        final List<NativeSyslogEventRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeSyslogEventRepository.AppendOutcome append(NativeSyslogEventRecord record) {
            records.add(record);
            return NativeSyslogEventRepository.AppendOutcome.INSERTED;
        }
    }
}
