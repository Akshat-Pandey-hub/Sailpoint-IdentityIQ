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
 * KF Agent REST read service for native work-item archives ({@code sailpoint.object.WorkItemArchive} →
 * {@code kf_workitem_archive}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeWorkItemArchiveClient} page source + {@link NativeWorkItemArchiveImportService} that the DB
 * path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No
 * existing extraction or DB code is modified; no PostgreSQL is touched on this path. The archive is an
 * append-only / immutable CEC history (the import has no deletion sweep), and this read endpoint preserves
 * that — it never writes.
 *
 * <p><b>REST contract</b> (verified field-by-field against {@link NativeWorkItemArchiveRepository}'s append
 * bindings): 29 SailPoint-facing fields. {@code signoffs}, {@code comments}, {@code owner_history},
 * {@code system_attributes} and {@code attributes} are the structured ({@code jsonb}) fields — returned as
 * JSON, not filterable. {@code is_signed} is a boolean; {@code created_at}/{@code modified_at}/
 * {@code expiration_ts}/{@code archived_ts} are ISO-8601 timestamps ({@code archived_ts} = the authoritative
 * archival timestamp); every other field is text. The 24 scalar fields are filterable. Excluded as
 * technical: the KeyForge {@code archiveid} PK (a canonical UUID), {@code record_hash}, and the entire CEC
 * lineage envelope ({@code src_system}/{@code src_object_type}/{@code src_object_id}/{@code src_natural_key}/
 * {@code src_created}/{@code src_modified}/{@code src_event_ts}/{@code src_interface}/{@code extraction_run_id}/
 * {@code raw_ref}/{@code derived_at}/{@code extracted_at}) — the {@code src_*} columns are lineage echoes of
 * the business fields ({@code src_object_id}=source_id, {@code src_created}=created_at, {@code src_modified}=
 * modifiedAfter is forwarded when supplied; the plugin applies the source-side filter where supported.
 * {@code modifiedAfter} is forwarded to the plugin when supplied; filtering is source-side where supported.
 */
public final class NativeWorkItemArchiveRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** The plugin's per-call maximum ({@code NativeWorkItemArchiveResource.MAX_LIMIT}); larger windows are chunked. */
    static final int PLUGIN_MAX_PAGE = 1000;

    private final NativeWorkItemArchiveParser parser = new NativeWorkItemArchiveParser();

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The five jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeWorkItemArchiveRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeWorkItemArchivePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        boolean filtered = filters != null && !filters.isEmpty();
        boolean windowed = limit != null && limit >= 0;
        int first = (start == null || start < 0) ? 0 : start;

        List<NativeWorkItemArchiveRecord> rows;
        if (filtered || windowed || first > 0) {
            SourceCountGuard guard = new SourceCountGuard("WorkItemArchive", parser::sourceCount);
            KfAgentPagedReader<NativeWorkItemArchiveRecord> reader = new KfAgentPagedReader<>(
                    (s, l) -> guard.check(source.fetchPage(s, l)), parser::parse, PLUGIN_MAX_PAGE,
                    INTERNAL_PAGE_SIZE);
            // start without limit reads from the offset to the end; the skipped rows are never fetched.
            rows = filtered
                    ? reader.filtered(r -> matches(r, filters), first, windowed ? limit : null)
                    : reader.window(first, windowed ? limit : Integer.MAX_VALUE);
        } else {
            rows = collectAll(source); // no filters, no start, no limit: the full snapshot
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (NativeWorkItemArchiveRecord r : rows) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeWorkItemArchiveRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the EXISTING (append-only) import; collecting sink = no DB. */
    private List<NativeWorkItemArchiveRecord> collectAll(NativeWorkItemArchivePageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // Append-only import (no sweep); the collecting sink persists nothing — no PostgreSQL is touched.
            new NativeWorkItemArchiveImportService(source, sink, INTERNAL_PAGE_SIZE).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native workitem-archive extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_workitem_archive order). */
    private Map<String, Object> toJson(NativeWorkItemArchiveRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("work_item_id", r.workItemId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("state", r.state);
        m.put("level", r.level);
        m.put("requester", r.requester);
        m.put("assignee", r.assignee);
        m.put("owner_name", r.ownerName);
        m.put("completer", r.completer);
        m.put("completion_comments", r.completionComments);
        m.put("is_signed", r.signed);                                 // boolean
        m.put("target_class", r.targetClass);
        m.put("target_id", r.targetId);
        m.put("target_name", r.targetName);
        m.put("identity_request_id", r.identityRequestId);
        m.put("certification_id", r.certificationId);
        m.put("certification_entity_id", r.certificationEntityId);
        m.put("certification_item_id", r.certificationItemId);
        m.put("entity_type", r.entityType);
        m.put("signoffs", node(r.signOffsJson));                      // jsonb
        m.put("comments", node(r.commentsJson));                      // jsonb
        m.put("owner_history", node(r.ownerHistoryJson));             // jsonb
        m.put("system_attributes", node(r.systemAttributesJson));     // jsonb
        m.put("attributes", node(r.attributesJson));                  // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("expiration_ts", iso(r.expiration));
        m.put("archived_ts", iso(r.archived));
        return m;
    }

    private static Map<String, Function<NativeWorkItemArchiveRecord, String>> buildScalars() {
        Map<String, Function<NativeWorkItemArchiveRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("work_item_id", r -> r.workItemId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("state", r -> r.state);
        m.put("level", r -> r.level);
        m.put("requester", r -> r.requester);
        m.put("assignee", r -> r.assignee);
        m.put("owner_name", r -> r.ownerName);
        m.put("completer", r -> r.completer);
        m.put("completion_comments", r -> r.completionComments);
        m.put("is_signed", r -> r.signed == null ? null : r.signed.toString());
        m.put("target_class", r -> r.targetClass);
        m.put("target_id", r -> r.targetId);
        m.put("target_name", r -> r.targetName);
        m.put("identity_request_id", r -> r.identityRequestId);
        m.put("certification_id", r -> r.certificationId);
        m.put("certification_entity_id", r -> r.certificationEntityId);
        m.put("certification_item_id", r -> r.certificationItemId);
        m.put("entity_type", r -> r.entityType);
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("expiration_ts", r -> iso(r.expiration));
        m.put("archived_ts", r -> iso(r.archived));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/array/object; null/empty stays null. */
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
    private static final class CollectingSink implements NativeWorkItemArchiveSink {
        final List<NativeWorkItemArchiveRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeWorkItemArchiveRepository.AppendOutcome append(NativeWorkItemArchiveRecord record) {
            records.add(record);
            return NativeWorkItemArchiveRepository.AppendOutcome.INSERTED;
        }
    }
}
