package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * KF Agent REST read service for native certification items ({@code sailpoint.object.CertificationItem} →
 * {@code kf_certification_item}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeCertificationItemClient} page source + {@link NativeCertificationItemImportService} that the DB
 * path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No
 * existing extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeCertificationItemRecord} +
 * {@link NativeCertificationItemParser} + {@code kf_certification_item}): 55 SailPoint-facing fields.
 * {@code application_names} and {@code classification_names} are the only structured ({@code jsonb}) fields —
 * returned as JSON, not filterable. 13 boolean fields ({@code iiq_elevated_access}/{@code reviewed}/
 * {@code delegated}/{@code acted_upon}/{@code historical}/{@code expired} + the 7 {@code action_is_*}) and
 * 8 ISO-8601 timestamps ({@code completed}/{@code last_decision}/{@code expiration_date}/{@code finished_date}/
 * {@code action_decision_date}/{@code action_mitigation_expiration}/{@code created_at}/{@code modified_at});
 * every other field is text. The 53 scalar fields are filterable. The KeyForge {@code certificationitemid}
 * PK, {@code record_hash}, lineage envelope and soft-delete columns are excluded. No native
 * {@code modifiedAfter} (the client has no server-side modified filter → full-scan only).
 */
public final class NativeCertificationItemRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The two jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeCertificationItemRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeCertificationItemPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeCertificationItemRecord> all = collectAll(source);

        List<NativeCertificationItemRecord> matched = new ArrayList<>();
        for (NativeCertificationItemRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeCertificationItemRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeCertificationItemRecord r, Map<String, String> filters) {
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
    private List<NativeCertificationItemRecord> collectAll(NativeCertificationItemPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeCertificationItemImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native certification-item extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_certification_item order). */
    private Map<String, Object> toJson(NativeCertificationItemRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("certification_id", r.certificationId);
        m.put("entity_id", r.entityId);
        m.put("identity", r.identity);
        m.put("type", r.type);
        m.put("sub_type", r.subType);
        m.put("bundle", r.bundle);
        m.put("bundle_assignment_id", r.bundleAssignmentId);
        m.put("exception_application", r.exceptionApplication);
        m.put("exception_attribute_name", r.exceptionAttributeName);
        m.put("exception_attribute_value", r.exceptionAttributeValue);
        m.put("exception_permission_target", r.exceptionPermissionTarget);
        m.put("exception_permission_right", r.exceptionPermissionRight);
        m.put("account_group", r.accountGroup);
        m.put("phase", r.phase);
        m.put("summary_status", r.summaryStatus);
        m.put("completed", iso(r.completed));
        m.put("last_decision", iso(r.lastDecision));
        m.put("expiration_date", iso(r.expirationDate));
        m.put("finished_date", iso(r.finishedDate));
        m.put("iiq_elevated_access", r.iiqElevatedAccess);
        m.put("reviewed", r.reviewed);
        m.put("delegated", r.delegated);
        m.put("acted_upon", r.actedUpon);
        m.put("historical", r.historical);
        m.put("expired", r.expired);
        m.put("target_id", r.targetId);
        m.put("target_name", r.targetName);
        m.put("short_description", r.shortDescription);
        m.put("violation_summary", r.violationSummary);
        m.put("application_names", node(r.applicationNamesJson));         // jsonb
        m.put("classification_names", node(r.classificationNamesJson));   // jsonb
        m.put("action_status", r.actionStatus);
        m.put("action_decision_date", iso(r.actionDecisionDate));
        m.put("action_decision_certification_id", r.actionDecisionCertificationId);
        m.put("action_remediation_action", r.actionRemediationAction);
        m.put("action_actor_name", r.actionActorName);
        m.put("action_actor_display_name", r.actionActorDisplayName);
        m.put("action_comments", r.actionComments);
        m.put("action_completion_comments", r.actionCompletionComments);
        m.put("action_owner_name", r.actionOwnerName);
        m.put("action_mitigation_expiration", iso(r.actionMitigationExpiration));
        m.put("action_is_approved", r.actionIsApproved);
        m.put("action_is_remediation", r.actionIsRemediation);
        m.put("action_is_mitigation", r.actionIsMitigation);
        m.put("action_is_delegation", r.actionIsDelegation);
        m.put("action_is_revoke_account", r.actionIsRevokeAccount);
        m.put("action_is_auto_decision", r.actionIsAutoDecision);
        m.put("action_is_bulk_certified", r.actionIsBulkCertified);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("policy_violation_id", r.policyViolationId);
        m.put("role_assignment", r.roleAssignment);
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeCertificationItemRecord, String>> buildScalars() {
        Map<String, Function<NativeCertificationItemRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("certification_id", r -> r.certificationId);
        m.put("entity_id", r -> r.entityId);
        m.put("identity", r -> r.identity);
        m.put("type", r -> r.type);
        m.put("sub_type", r -> r.subType);
        m.put("bundle", r -> r.bundle);
        m.put("bundle_assignment_id", r -> r.bundleAssignmentId);
        m.put("exception_application", r -> r.exceptionApplication);
        m.put("exception_attribute_name", r -> r.exceptionAttributeName);
        m.put("exception_attribute_value", r -> r.exceptionAttributeValue);
        m.put("exception_permission_target", r -> r.exceptionPermissionTarget);
        m.put("exception_permission_right", r -> r.exceptionPermissionRight);
        m.put("account_group", r -> r.accountGroup);
        m.put("phase", r -> r.phase);
        m.put("summary_status", r -> r.summaryStatus);
        m.put("completed", r -> iso(r.completed));
        m.put("last_decision", r -> iso(r.lastDecision));
        m.put("expiration_date", r -> iso(r.expirationDate));
        m.put("finished_date", r -> iso(r.finishedDate));
        m.put("iiq_elevated_access", r -> str(r.iiqElevatedAccess));
        m.put("reviewed", r -> str(r.reviewed));
        m.put("delegated", r -> str(r.delegated));
        m.put("acted_upon", r -> str(r.actedUpon));
        m.put("historical", r -> str(r.historical));
        m.put("expired", r -> str(r.expired));
        m.put("target_id", r -> r.targetId);
        m.put("target_name", r -> r.targetName);
        m.put("short_description", r -> r.shortDescription);
        m.put("violation_summary", r -> r.violationSummary);
        m.put("action_status", r -> r.actionStatus);
        m.put("action_decision_date", r -> iso(r.actionDecisionDate));
        m.put("action_decision_certification_id", r -> r.actionDecisionCertificationId);
        m.put("action_remediation_action", r -> r.actionRemediationAction);
        m.put("action_actor_name", r -> r.actionActorName);
        m.put("action_actor_display_name", r -> r.actionActorDisplayName);
        m.put("action_comments", r -> r.actionComments);
        m.put("action_completion_comments", r -> r.actionCompletionComments);
        m.put("action_owner_name", r -> r.actionOwnerName);
        m.put("action_mitigation_expiration", r -> iso(r.actionMitigationExpiration));
        m.put("action_is_approved", r -> str(r.actionIsApproved));
        m.put("action_is_remediation", r -> str(r.actionIsRemediation));
        m.put("action_is_mitigation", r -> str(r.actionIsMitigation));
        m.put("action_is_delegation", r -> str(r.actionIsDelegation));
        m.put("action_is_revoke_account", r -> str(r.actionIsRevokeAccount));
        m.put("action_is_auto_decision", r -> str(r.actionIsAutoDecision));
        m.put("action_is_bulk_certified", r -> str(r.actionIsBulkCertified));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("policy_violation_id", r -> r.policyViolationId);
        m.put("role_assignment", r -> r.roleAssignment);
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
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

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeCertificationItemSink {
        final List<NativeCertificationItemRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeCertificationItemRepository.UpsertOutcome upsert(NativeCertificationItemRecord record) {
            records.add(record);
            return NativeCertificationItemRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
