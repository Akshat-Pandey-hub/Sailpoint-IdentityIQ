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
 * KF Agent REST read service for native certification archives ({@code sailpoint.object.CertificationArchive}
 * → {@code kf_certification_archive}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeCertificationArchiveClient} page source + {@link NativeCertificationArchiveImportService} that
 * the DB path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No
 * existing extraction or DB code is modified; no PostgreSQL is touched on this path. The archive is an
 * append-only / immutable CEC history (the import has no deletion sweep), and this read endpoint preserves
 * that — it never writes.
 *
 * <p><b>REST contract</b> (verified field-by-field against {@link NativeCertificationArchiveRepository}'s
 * append bindings): 14 SailPoint-facing fields. {@code child_certification_ids} is a {@code jsonb} array
 * (returned as JSON) and {@code archive_xml} is the raw historical archive XML document (a large text blob
 * preserved verbatim); both are structured and NOT filterable. {@code signed}/{@code expiration}/
 * {@code created_at}/{@code modified_at}/{@code archived_ts} are ISO-8601 timestamps ({@code archived_ts} is
 * the archival timestamp — for this entity it equals {@code created_at} by design); every other field is text.
 * The 12 scalar fields are filterable. Excluded as technical: the KeyForge {@code certificationarchiveid} PK
 * (a canonical UUID), {@code record_hash}, and the entire CEC lineage envelope ({@code src_system}/
 * {@code src_object_type}/{@code src_object_id}/{@code src_natural_key}/{@code src_created}/{@code src_modified}/
 * {@code src_event_ts}/{@code src_interface}/{@code extraction_run_id}/{@code raw_ref}/{@code derived_at}/
 * {@code extracted_at}) — the {@code src_*} columns are lineage echoes of the business fields
 * ({@code src_object_id}=source_id, {@code src_created}=created_at, {@code src_modified}=modified_at,
 * {@code src_event_ts}=created), not additional business data. No native {@code modifiedAfter} (the client has
 * no server-side modified filter → full-scan only).
 */
public final class NativeCertificationArchiveRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. {@code child_certification_ids} + {@code archive_xml} are NOT filterable. */
    private static final Map<String, Function<NativeCertificationArchiveRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeCertificationArchivePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeCertificationArchiveRecord> all = collectAll(source);

        List<NativeCertificationArchiveRecord> matched = new ArrayList<>();
        for (NativeCertificationArchiveRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeCertificationArchiveRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeCertificationArchiveRecord r, Map<String, String> filters) {
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
    private List<NativeCertificationArchiveRecord> collectAll(NativeCertificationArchivePageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // Append-only import (no sweep); the collecting sink persists nothing — no PostgreSQL is touched.
            new NativeCertificationArchiveImportService(source, sink, INTERNAL_PAGE_SIZE).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native certification-archive extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_certification_archive order). */
    private Map<String, Object> toJson(NativeCertificationArchiveRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("certification_id", r.certificationId);
        m.put("certification_group_id", r.certificationGroupId);
        m.put("creator_name", r.creatorName);
        m.put("owner_name", r.ownerName);
        m.put("comments", r.comments);
        m.put("signed", iso(r.signed));
        m.put("expiration", iso(r.expiration));
        m.put("child_certification_ids", node(r.childCertificationIdsJson));   // jsonb array
        m.put("archive_xml", r.archiveXml);                                    // raw historical XML document (text)
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("archived_ts", iso(r.created));                                  // archival timestamp (= created_at by design)
        return m;
    }

    private static Map<String, Function<NativeCertificationArchiveRecord, String>> buildScalars() {
        Map<String, Function<NativeCertificationArchiveRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("certification_id", r -> r.certificationId);
        m.put("certification_group_id", r -> r.certificationGroupId);
        m.put("creator_name", r -> r.creatorName);
        m.put("owner_name", r -> r.ownerName);
        m.put("comments", r -> r.comments);
        m.put("signed", r -> iso(r.signed));
        m.put("expiration", r -> iso(r.expiration));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("archived_ts", r -> iso(r.created));
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
    private static final class CollectingSink implements NativeCertificationArchiveSink {
        final List<NativeCertificationArchiveRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeCertificationArchiveRepository.AppendOutcome append(NativeCertificationArchiveRecord record) {
            records.add(record);
            return NativeCertificationArchiveRepository.AppendOutcome.INSERTED;
        }
    }
}
