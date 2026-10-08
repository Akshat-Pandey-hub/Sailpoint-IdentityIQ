package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * KF Agent REST read service for native audit events ({@code sailpoint.object.AuditEvent} →
 * {@code kf_audit_event}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeAuditEventClient} page source + {@link NativeAuditEventImportService} that the DB path uses,
 * with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No existing
 * extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeAuditEventRecord} + {@link NativeAuditEventParser} +
 * {@code kf_audit_event}): 19 SailPoint-facing fields. {@code attributes} is the only structured
 * ({@code jsonb}) field — returned as JSON, not filterable. {@code created_at} is an ISO-8601 timestamp;
 * every other field is text. The 18 scalar fields are filterable. The KeyForge {@code auditid} PK,
 * {@code record_hash} and the lineage envelope ({@code source_system}/{@code source_interface}/
 * {@code source_object_type}/{@code extraction_run_id}/{@code extracted_at}) are excluded. AuditEvent is
 * append-only (no soft-delete columns). No native {@code modifiedAfter} (the client has no server-side
 * modified filter → full-scan only).
 */
public final class NativeAuditEventRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code attributes} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeAuditEventRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeAuditEventPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeAuditEventRecord> all = collectAll(source);

        List<NativeAuditEventRecord> matched = new ArrayList<>();
        for (NativeAuditEventRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeAuditEventRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeAuditEventRecord r, Map<String, String> filters) {
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
    private List<NativeAuditEventRecord> collectAll(NativeAuditEventPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // Append-only import (no sweep); the collecting sink persists nothing — no PostgreSQL is touched.
            new NativeAuditEventImportService(source, sink, INTERNAL_PAGE_SIZE).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native audit-event extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_audit_event order). */
    private Map<String, Object> toJson(NativeAuditEventRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("action", r.action);
        m.put("audit_source", r.auditSource);
        m.put("target", r.target);
        m.put("application", r.application);
        m.put("account_name", r.accountName);
        m.put("instance", r.instance);
        m.put("attribute_name", r.attributeName);
        m.put("attribute_value", r.attributeValue);
        m.put("interface_name", r.interfaceName);
        m.put("server_host", r.serverHost);
        m.put("client_host", r.clientHost);
        m.put("tracking_id", r.trackingId);
        m.put("string1", r.string1);
        m.put("string2", r.string2);
        m.put("string3", r.string3);
        m.put("string4", r.string4);
        m.put("attributes", node(r.attributesJson));   // jsonb
        m.put("created_at", iso(r.created));
        return m;
    }

    private static Map<String, Function<NativeAuditEventRecord, String>> buildScalars() {
        Map<String, Function<NativeAuditEventRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("action", r -> r.action);
        m.put("audit_source", r -> r.auditSource);
        m.put("target", r -> r.target);
        m.put("application", r -> r.application);
        m.put("account_name", r -> r.accountName);
        m.put("instance", r -> r.instance);
        m.put("attribute_name", r -> r.attributeName);
        m.put("attribute_value", r -> r.attributeValue);
        m.put("interface_name", r -> r.interfaceName);
        m.put("server_host", r -> r.serverHost);
        m.put("client_host", r -> r.clientHost);
        m.put("tracking_id", r -> r.trackingId);
        m.put("string1", r -> r.string1);
        m.put("string2", r -> r.string2);
        m.put("string3", r -> r.string3);
        m.put("string4", r -> r.string4);
        m.put("created_at", r -> iso(r.created));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/object/array; null/empty stays null. */
    private Object node(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(json);
            return n == null || n.isNull() ? null : n;
        } catch (Exception e) {
            return json;
        }
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    /** In-memory append-only sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeAuditEventSink {
        final List<NativeAuditEventRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeAuditEventRepository.AppendOutcome append(NativeAuditEventRecord record) {
            records.add(record);
            return NativeAuditEventRepository.AppendOutcome.INSERTED;
        }
    }
}
