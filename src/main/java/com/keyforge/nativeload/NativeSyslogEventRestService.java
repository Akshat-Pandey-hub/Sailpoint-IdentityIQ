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

    /** Scalar response field name -> value extractor (all 11 scalar, all filterable). */
    private static final Map<String, Function<NativeSyslogEventRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeSyslogEventPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeSyslogEventRecord> all = collectAll(source);

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
    private List<NativeSyslogEventRecord> collectAll(NativeSyslogEventPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // Append-only import (no sweep); the collecting sink persists nothing — no PostgreSQL is touched.
            new NativeSyslogEventImportService(source, sink, INTERNAL_PAGE_SIZE).importAll();
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
