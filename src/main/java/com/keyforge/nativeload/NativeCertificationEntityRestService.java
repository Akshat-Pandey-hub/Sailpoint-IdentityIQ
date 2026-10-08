package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native certification entities ({@code sailpoint.object.CertificationEntity}
 * → {@code kf_certification_entity}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeCertificationEntityClient} page source + {@link NativeCertificationEntityImportService} that
 * the DB path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass).
 * No existing extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeCertificationEntityRecord} +
 * {@link NativeCertificationEntityParser} + {@code kf_certification_entity}): 32 SailPoint-facing fields, all
 * scalar (no nested/jsonb fields). {@code entity_delegated} is a boolean; {@code composite_score} is an
 * integer; {@code completed}/{@code created_at}/{@code modified_at}/{@code action_decision_date} are ISO-8601
 * timestamps; every other field is text. All 32 are filterable. The KeyForge {@code certificationentityid}
 * PK, {@code record_hash}, lineage envelope ({@code source_system}/{@code source_interface}/
 * {@code source_object_type}/{@code extraction_run_id}/{@code extracted_at}) and soft-delete columns are
 * excluded. No native {@code modifiedAfter} (the client has no server-side modified filter → full-scan only).
 */
public final class NativeCertificationEntityRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Response field name -> value extractor (all scalar, all filterable). */
    private static final Map<String, Function<NativeCertificationEntityRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeCertificationEntityPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeCertificationEntityRecord> all = collectAll(source);

        List<NativeCertificationEntityRecord> matched = new ArrayList<>();
        for (NativeCertificationEntityRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeCertificationEntityRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeCertificationEntityRecord r, Map<String, String> filters) {
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
    private List<NativeCertificationEntityRecord> collectAll(NativeCertificationEntityPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeCertificationEntityImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native certification-entity extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_certification_entity order). */
    private Map<String, Object> toJson(NativeCertificationEntityRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("certification_id", r.certificationId);
        m.put("identity", r.identity);
        m.put("application", r.application);
        m.put("native_identity", r.nativeIdentity);
        m.put("account_group", r.accountGroup);
        m.put("first_name", r.firstName);
        m.put("last_name", r.lastName);
        m.put("full_name", r.fullName);
        m.put("reference_attribute", r.referenceAttribute);
        m.put("schema_object_type", r.schemaObjectType);
        m.put("snapshot_id", r.snapshotId);
        m.put("pending_certification", r.pendingCertification);
        m.put("type", r.type);
        m.put("summary_status", r.summaryStatus);
        m.put("entity_delegated", r.entityDelegated);                 // boolean
        m.put("entity_delegation_status", r.entityDelegationStatus);
        m.put("composite_score", r.compositeScore);                   // integer
        m.put("target_id", r.targetId);
        m.put("target_name", r.targetName);
        m.put("target_display_name", r.targetDisplayName);
        m.put("completed", iso(r.completed));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("action_status", r.actionStatus);
        m.put("action_decision_date", iso(r.actionDecisionDate));
        m.put("action_remediation_action", r.actionRemediationAction);
        m.put("action_actor_name", r.actionActorName);
        m.put("action_actor_display_name", r.actionActorDisplayName);
        m.put("action_comments", r.actionComments);
        return m;
    }

    private static Map<String, Function<NativeCertificationEntityRecord, String>> buildScalars() {
        Map<String, Function<NativeCertificationEntityRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("certification_id", r -> r.certificationId);
        m.put("identity", r -> r.identity);
        m.put("application", r -> r.application);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("account_group", r -> r.accountGroup);
        m.put("first_name", r -> r.firstName);
        m.put("last_name", r -> r.lastName);
        m.put("full_name", r -> r.fullName);
        m.put("reference_attribute", r -> r.referenceAttribute);
        m.put("schema_object_type", r -> r.schemaObjectType);
        m.put("snapshot_id", r -> r.snapshotId);
        m.put("pending_certification", r -> r.pendingCertification);
        m.put("type", r -> r.type);
        m.put("summary_status", r -> r.summaryStatus);
        m.put("entity_delegated", r -> r.entityDelegated == null ? null : r.entityDelegated.toString());
        m.put("entity_delegation_status", r -> r.entityDelegationStatus);
        m.put("composite_score", r -> r.compositeScore == null ? null : String.valueOf(r.compositeScore));
        m.put("target_id", r -> r.targetId);
        m.put("target_name", r -> r.targetName);
        m.put("target_display_name", r -> r.targetDisplayName);
        m.put("completed", r -> iso(r.completed));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("action_status", r -> r.actionStatus);
        m.put("action_decision_date", r -> iso(r.actionDecisionDate));
        m.put("action_remediation_action", r -> r.actionRemediationAction);
        m.put("action_actor_name", r -> r.actionActorName);
        m.put("action_actor_display_name", r -> r.actionActorDisplayName);
        m.put("action_comments", r -> r.actionComments);
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeCertificationEntitySink {
        final List<NativeCertificationEntityRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeCertificationEntityRepository.UpsertOutcome upsert(NativeCertificationEntityRecord record) {
            records.add(record);
            return NativeCertificationEntityRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
