package com.keyforge.iiq.parquet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.accessrequest.AccessRequest;
import com.keyforge.iiq.accessrequest.AccessRequestRow;
import com.keyforge.iiq.accessrequest.AccessRequestRowMapper;
import com.keyforge.iiq.accessrequest.AccessRequestService;
import com.keyforge.iiq.accessrequest.RequestApprovalRow;
import com.keyforge.iiq.accessrequest.RequestItemRow;
import com.keyforge.iiq.account.AccountService;
import com.keyforge.iiq.auditevent.AuditEvent;
import com.keyforge.iiq.auditevent.AuditEventService;
import com.keyforge.iiq.entitlement.EntitlementService;
import com.keyforge.iiq.eventlink.EventLinkResolver;
import com.keyforge.iiq.identity.IdentityService;
import com.keyforge.iiq.model.Account;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Identity;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItem;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItemRow;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItemRowMapper;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItemService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTransaction;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTransactionService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTxnRow;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTxnRowMapper;
import com.keyforge.iiq.taskresult.TaskResult;
import com.keyforge.iiq.taskresult.TaskResultRow;
import com.keyforge.iiq.taskresult.TaskResultRowMapper;
import com.keyforge.iiq.violation.PolicyViolation;
import com.keyforge.iiq.violation.ViolationRow;
import com.keyforge.iiq.violation.ViolationRowMapper;
import com.keyforge.iiq.violation.ViolationService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.keyforge.iiq.parquet.ParquetType.BOOL;
import static com.keyforge.iiq.parquet.ParquetType.INT;
import static com.keyforge.iiq.parquet.ParquetType.JSON;
import static com.keyforge.iiq.parquet.ParquetType.STRING;
import static com.keyforge.iiq.parquet.ParquetType.TIMESTAMP;
import static com.keyforge.iiq.parquet.ParquetType.UUID_STR;

/**
 * Declarative registry of the Parquet datasets: for each, its schema (business columns; the lineage
 * envelope is appended by {@link DatasetSpec}) and an extractor that pulls directly from IIQ (reusing
 * the existing Services + DB RowMappers) and returns lineage-stamped rows. No PostgreSQL is involved.
 */
public final class ParquetDatasets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionalInterface
    public interface Extractor {
        List<Map<String, Object>> rows(ExtractionContext ctx);
    }

    public record Entry(DatasetSpec spec, Extractor extractor) {
    }

    private final LinkedHashMap<String, Entry> registry = new LinkedHashMap<>();

    public ParquetDatasets() {
        register(taskResult());
        register(auditEvent());
        register(accessRequest());
        register(requestItem());
        register(requestApproval());
        register(provisioningTxn());
        register(provisioningItem());
        register(violation());
        register(certItemDecision());
        register(eventLink());
    }

    private void register(Entry e) {
        registry.put(e.spec().name(), e);
    }

    public List<String> names() {
        return new ArrayList<>(registry.keySet());
    }

    public Entry get(String name) {
        return registry.get(name);
    }

    // ==================================================================================
    // Dataset definitions
    // ==================================================================================

    private Entry taskResult() {
        List<Column> cols = List.of(
                col("taskresultid", UUID_STR), col("source_id", STRING), col("name", STRING),
                col("type", STRING), col("task_definition", STRING), col("completion_status", STRING),
                col("host", STRING), col("launcher", STRING), col("launched", TIMESTAMP),
                col("completed", TIMESTAMP), col("partitioned", BOOL), col("terminated", BOOL),
                col("pending_signoffs", INT), col("messages", JSON));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            List<TaskResult> results = ctx.memo("taskResults",
                    () -> new com.keyforge.iiq.taskresult.TaskResultService(ctx.api()).getAllTaskResults());
            for (TaskResult t : results) {
                TaskResultRow r = TaskResultRowMapper.map(t);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("taskresultid", r.taskresultid()); m.put("source_id", r.sourceId()); m.put("name", r.name());
                m.put("type", r.type()); m.put("task_definition", r.taskDefinition());
                m.put("completion_status", r.completionStatus()); m.put("host", r.host());
                m.put("launcher", r.launcher()); m.put("launched", r.launched()); m.put("completed", r.completed());
                m.put("partitioned", r.partitioned()); m.put("terminated", r.terminated());
                m.put("pending_signoffs", r.pendingSignoffs()); m.put("messages", r.messagesJson());
                out.add(stamp(m, new Lineage.Envelope("TaskResult", r.sourceId(), r.name(),
                        null, null, inst(r.completed()), "scim", null), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("task_result", cols), ex);
    }

    private Entry auditEvent() {
        List<Column> cols = List.of(
                col("audit_event_id", UUID_STR), col("source_id", STRING), col("action", STRING),
                col("source", STRING), col("target", STRING), col("created", STRING));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (AuditEvent a : auditEvents(ctx)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("audit_event_id", ParquetIds.canonicalUuid(a.id())); m.put("source_id", a.id());
                m.put("action", a.action()); m.put("source", a.source()); m.put("target", a.target());
                m.put("created", a.created());
                out.add(stamp(m, new Lineage.Envelope("AuditEvent", a.id(), null,
                        null, null, null, "classic-ui", null), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_audit_event", cols), ex);
    }

    private Entry accessRequest() {
        List<Column> cols = List.of(
                col("requestid", UUID_STR), col("request_number", STRING), col("type", STRING),
                col("requester_display_name", STRING), col("target_display_name", STRING), col("state", STRING),
                col("execution_status", STRING), col("completion_status", STRING), col("priority", STRING),
                col("external_ticket_id", STRING), col("cancelable", BOOL), col("created_at", TIMESTAMP),
                col("end_date", TIMESTAMP), col("terminated_date", TIMESTAMP), col("verification_date", TIMESTAMP),
                col("item_count", INT));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (AccessRequest req : accessRequests(ctx)) {
                AccessRequestRow r = AccessRequestRowMapper.mapRequest(req);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("requestid", r.requestid()); m.put("request_number", r.requestNumber()); m.put("type", r.type());
                m.put("requester_display_name", r.requesterDisplayName());
                m.put("target_display_name", r.targetDisplayName()); m.put("state", r.state());
                m.put("execution_status", r.executionStatus()); m.put("completion_status", r.completionStatus());
                m.put("priority", r.priority()); m.put("external_ticket_id", r.externalTicketId());
                m.put("cancelable", r.cancelable()); m.put("created_at", r.createdAt()); m.put("end_date", r.endDate());
                m.put("terminated_date", r.terminatedDate()); m.put("verification_date", r.verificationDate());
                m.put("item_count", r.itemCount());
                out.add(stamp(m, new Lineage.Envelope("IdentityRequest", req.id(), r.requestNumber(),
                        inst(r.createdAt()), null, null, "ui-rest", null), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_access_request", cols), ex);
    }

    private Entry requestItem() {
        List<Column> cols = List.of(
                col("itemid", UUID_STR), col("requestid", UUID_STR), col("request_number", STRING),
                col("operation", STRING), col("application_name", STRING), col("account_name", STRING),
                col("displayable_account_name", STRING), col("instance", STRING), col("name", STRING),
                col("value", STRING), col("displayable_value", STRING), col("is_role", BOOL),
                col("is_entitlement", BOOL), col("has_managed_attribute", BOOL), col("approval_state", STRING),
                col("provisioning_state", STRING), col("provisioning_engine", STRING),
                col("provisioning_request_id", STRING), col("assignment_id", STRING), col("retries", INT),
                col("requester_comments", STRING), col("start_date", TIMESTAMP), col("end_date", TIMESTAMP));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (AccessRequest req : accessRequests(ctx)) {
                for (AccessRequest.Item it : req.items()) {
                    RequestItemRow r = AccessRequestRowMapper.mapItem(req, it);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("itemid", r.itemid()); m.put("requestid", r.requestid());
                    m.put("request_number", r.requestNumber()); m.put("operation", r.operation());
                    m.put("application_name", r.applicationName()); m.put("account_name", r.accountName());
                    m.put("displayable_account_name", r.displayableAccountName()); m.put("instance", r.instance());
                    m.put("name", r.name()); m.put("value", r.value()); m.put("displayable_value", r.displayableValue());
                    m.put("is_role", r.isRole()); m.put("is_entitlement", r.isEntitlement());
                    m.put("has_managed_attribute", r.hasManagedAttribute()); m.put("approval_state", r.approvalState());
                    m.put("provisioning_state", r.provisioningState()); m.put("provisioning_engine", r.provisioningEngine());
                    m.put("provisioning_request_id", r.provisioningRequestId()); m.put("assignment_id", r.assignmentId());
                    m.put("retries", r.retries()); m.put("requester_comments", r.requesterComments());
                    m.put("start_date", r.startDate()); m.put("end_date", r.endDate());
                    out.add(stamp(m, new Lineage.Envelope("IdentityRequestItem", it.id(), null,
                            null, null, null, "ui-rest", r.requestid()), ctx));
                }
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_request_item", cols), ex);
    }

    private Entry requestApproval() {
        List<Column> cols = List.of(
                col("id", UUID_STR), col("requestid", UUID_STR), col("request_number", STRING),
                col("owner_display_name", STRING), col("status", STRING), col("description", STRING),
                col("comments", STRING), col("approval_item_count", INT), col("work_item_id", UUID_STR),
                col("work_item_name", STRING), col("work_item_archive_id", STRING),
                col("open_date", TIMESTAMP), col("complete_date", TIMESTAMP));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (AccessRequest req : accessRequests(ctx)) {
                int idx = 0;
                for (AccessRequest.Approval a : req.interactions()) {
                    RequestApprovalRow r = AccessRequestRowMapper.mapApproval(req, a, idx++);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.id()); m.put("requestid", r.requestid()); m.put("request_number", r.requestNumber());
                    m.put("owner_display_name", r.ownerDisplayName()); m.put("status", r.status());
                    m.put("description", r.description()); m.put("comments", r.comments());
                    m.put("approval_item_count", r.approvalItemCount()); m.put("work_item_id", r.workItemId());
                    m.put("work_item_name", r.workItemName()); m.put("work_item_archive_id", r.workItemArchiveId());
                    m.put("open_date", r.openDate()); m.put("complete_date", r.completeDate());
                    out.add(stamp(m, new Lineage.Envelope("IdentityRequestApproval", r.id(), r.requestNumber(),
                            inst(r.openDate()), null, inst(r.completeDate()), "ui-rest", r.requestid()), ctx));
                }
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_request_approval", cols), ex);
    }

    private Entry provisioningTxn() {
        List<Column> cols = List.of(
                col("txnid", UUID_STR), col("source_id", STRING), col("name", STRING), col("operation", STRING),
                col("source", STRING), col("status", STRING), col("status_message", STRING), col("type", STRING),
                col("type_message", STRING), col("integration", STRING), col("identity_name", STRING),
                col("identity_display_name", STRING), col("application_name", STRING), col("native_identity", STRING),
                col("account_display_name", STRING), col("created_display", STRING), col("created_at", TIMESTAMP),
                col("modified_at", TIMESTAMP), col("last_retry", TIMESTAMP), col("ticket_id", STRING),
                col("retry", BOOL), col("retry_count", INT), col("timed_out", BOOL), col("forced", BOOL),
                col("forceable", BOOL), col("result", STRING), col("access_request_id", STRING),
                col("certification_name", STRING));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            List<ProvisioningTransaction> txns = provTxns(ctx);
            for (ProvisioningTransaction t : txns) {
                ProvisioningTxnRow r = ProvisioningTxnRowMapper.map(t);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("txnid", r.txnid()); m.put("source_id", r.sourceId()); m.put("name", r.name());
                m.put("operation", r.operation()); m.put("source", r.source()); m.put("status", r.status());
                m.put("status_message", r.statusMessage()); m.put("type", r.type()); m.put("type_message", r.typeMessage());
                m.put("integration", r.integration()); m.put("identity_name", r.identityName());
                m.put("identity_display_name", r.identityDisplayName()); m.put("application_name", r.applicationName());
                m.put("native_identity", r.nativeIdentity()); m.put("account_display_name", r.accountDisplayName());
                m.put("created_display", r.createdDisplay()); m.put("created_at", r.createdAt());
                m.put("modified_at", r.modifiedAt()); m.put("last_retry", r.lastRetry()); m.put("ticket_id", r.ticketId());
                m.put("retry", r.retry()); m.put("retry_count", r.retryCount()); m.put("timed_out", r.timedOut());
                m.put("forced", r.forced()); m.put("forceable", r.forceable()); m.put("result", r.result());
                m.put("access_request_id", r.accessRequestId()); m.put("certification_name", r.certificationName());
                out.add(stamp(m, new Lineage.Envelope("ProvisioningTransaction", r.sourceId(), r.name(),
                        inst(r.createdAt()), inst(r.modifiedAt()), null, "classic-rest", null), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_provisioning_txn", cols), ex);
    }

    private Entry provisioningItem() {
        List<Column> cols = List.of(
                col("itemid", UUID_STR), col("txnid", UUID_STR), col("source_txn_id", STRING),
                col("request_type", STRING), col("item_index", INT), col("operation", STRING), col("name", STRING),
                col("value", STRING), col("result", STRING), col("reason", STRING),
                col("is_attribute_request", BOOL), col("error_messages", JSON));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            List<ProvisioningItem> items = ctx.memo("provItems",
                    () -> new ProvisioningItemService(ctx.session()).getAllItems());
            for (ProvisioningItem it : items) {
                ProvisioningItemRow r = ProvisioningItemRowMapper.map(it);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemid", r.itemid()); m.put("txnid", r.txnid()); m.put("source_txn_id", r.sourceTxnId());
                m.put("request_type", r.requestType()); m.put("item_index", r.itemIndex());
                m.put("operation", r.operation()); m.put("name", r.name()); m.put("value", r.value());
                m.put("result", r.result()); m.put("reason", r.reason());
                m.put("is_attribute_request", r.attributeRequest()); m.put("error_messages", r.errorMessagesJson());
                out.add(stamp(m, new Lineage.Envelope("ProvisioningTransactionItem", r.sourceTxnId(), r.name(),
                        null, null, null, "classic-rest", r.txnid()), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_provisioning_item", cols), ex);
    }

    private Entry violation() {
        List<Column> cols = List.of(
                col("violationid", UUID_STR), col("policy_name", STRING), col("constraint_name", STRING),
                col("status", STRING), col("description", STRING), col("owner_id", UUID_STR),
                col("owner_display_name", STRING), col("identity_id", UUID_STR),
                col("identity_display_name", STRING), col("mitigator", STRING), col("expiration_date", TIMESTAMP));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            List<PolicyViolation> vs = new ViolationService(ctx.api()).getAllViolations();
            for (PolicyViolation v : vs) {
                ViolationRow r = ViolationRowMapper.map(v);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("violationid", r.violationid()); m.put("policy_name", r.policyName());
                m.put("constraint_name", r.constraintName()); m.put("status", r.status());
                m.put("description", r.description()); m.put("owner_id", r.ownerId());
                m.put("owner_display_name", r.ownerDisplayName()); m.put("identity_id", r.identityId());
                m.put("identity_display_name", r.identityDisplayName()); m.put("mitigator", r.mitigator());
                m.put("expiration_date", r.expirationDate());
                out.add(stamp(m, new Lineage.Envelope("PolicyViolation", v.id(), r.policyName(),
                        null, null, null, "scim", null), ctx));
            }
            return out;
        };
        return new Entry(new DatasetSpec("kf_violation", cols), ex);
    }

    /**
     * Certification item decisions. The campaign→entity→item→decision hierarchy is NOT reachable
     * read-only on this instance (the admin drill-in {@code ui/rest/certificationGroups/{id}/...}
     * returns HTTP 500, no plugin, certifier-scoped). The schema is defined per the PDF; the extractor
     * returns zero rows (source-limited) rather than fabricating decisions.
     */
    private Entry certItemDecision() {
        List<Column> cols = List.of(
                col("decision_id", UUID_STR), col("campaign_id", UUID_STR), col("certification_id", UUID_STR),
                col("entity_id", UUID_STR), col("identity_id", UUID_STR), col("item_id", UUID_STR),
                col("item_type", STRING), col("application", STRING), col("attribute", STRING), col("value", STRING),
                col("decision", STRING), col("decider", STRING), col("decision_at", TIMESTAMP),
                col("comments", STRING), col("sign_off_id", UUID_STR), col("remediation_id", UUID_STR));
        Extractor ex = ctx -> new ArrayList<>(); // source-limited: no reachable decision source
        return new Entry(new DatasetSpec("kf_cert_item_decision", cols), ex);
    }

    /**
     * Derived event links (PDF: audit event → identity/account/entitlement, and provisioning
     * transaction → the request/certification that caused it). Built directly from freshly-extracted
     * IIQ data — never from PostgreSQL. Audit-side resolution reuses the pure {@link EventLinkResolver}
     * with name→id maps from extracted identities/accounts/entitlements; provisioning-side links come
     * from the transaction detail's explicit {@code accessRequestId}/{@code certificationName}.
     */
    private Entry eventLink() {
        List<Column> cols = List.of(
                col("id", UUID_STR), col("source_object_type", STRING), col("source_object_id", UUID_STR),
                col("target_object_type", STRING), col("target_object_id", UUID_STR), col("target_raw", STRING),
                col("target_type_hint", STRING), col("link_status", STRING), col("resolution_rule", STRING));
        Extractor ex = ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            addAuditLinks(ctx, out);
            addProvisioningLinks(ctx, out);
            return out;
        };
        return new Entry(new DatasetSpec("kf_event_link", cols), ex);
    }

    // ---- event-link derivation helpers -------------------------------------

    private void addAuditLinks(ExtractionContext ctx, List<Map<String, Object>> out) {
        EventLinkResolver resolver = new EventLinkResolver(
                nameMap(identities(ctx), Identity::getUserName, Identity::getDisplayName, i -> ParquetIds.canonicalUuid(i.getId())),
                nameMap(accounts(ctx), Account::getNativeIdentity, Account::getDisplayName, a -> ParquetIds.canonicalUuid(a.getId())),
                nameMap(entitlements(ctx), Entitlement::getValue, Entitlement::getDisplayableName, e -> ParquetIds.canonicalUuid(e.getId())));
        for (AuditEvent a : auditEvents(ctx)) {
            String target = a.target();
            if (target == null || target.isBlank()) {
                continue; // no target -> no link (never fabricated)
            }
            String auditId = ParquetIds.canonicalUuid(a.id());
            EventLinkResolver.Result r = resolver.resolve(target);
            String id = ParquetIds.deterministicUuid("AUDIT_TARGET|" + auditId + "|" + target);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("source_object_type", "AuditEvent"); m.put("source_object_id", auditId);
            m.put("target_object_type", r.targetType()); m.put("target_object_id", r.targetId());
            m.put("target_raw", target); m.put("target_type_hint", r.typeHint());
            m.put("link_status", r.status().name()); m.put("resolution_rule", r.rule());
            out.add(stamp(m, new Lineage.Envelope("EventLink", id, null, null, null, null, "derived", auditId), ctx));
        }
    }

    private void addProvisioningLinks(ExtractionContext ctx, List<Map<String, Object>> out) {
        List<String> ids = ctx.memo("provTxnIds",
                () -> new ProvisioningItemService(ctx.session()).getAllTransactionIds());
        for (String txnId : ids) {
            JsonNode d;
            try {
                d = MAPPER.readTree(ctx.session().get("rest/provisioningTransactions/" + txnId, null));
            } catch (Exception e) {
                continue;
            }
            String canonTxn = ParquetIds.canonicalUuid(txnId);
            addRefLink(out, ctx, canonTxn, txnId, text(d, "accessRequestId"), "IdentityRequest",
                    "accessRequestId", "provisioningTransaction.accessRequestId");
            addRefLink(out, ctx, canonTxn, txnId, text(d, "certificationName"), "Certification",
                    "certificationName", "provisioningTransaction.certificationName");
        }
    }

    private void addRefLink(List<Map<String, Object>> out, ExtractionContext ctx, String canonTxn, String rawTxn,
                            String rawTarget, String targetType, String typeHint, String rule) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return;
        }
        String id = ParquetIds.deterministicUuid("PROV_" + typeHint + "|" + rawTxn + "|" + rawTarget);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("source_object_type", "ProvisioningTransaction"); m.put("source_object_id", canonTxn);
        m.put("target_object_type", targetType); m.put("target_object_id", null);
        m.put("target_raw", rawTarget); m.put("target_type_hint", typeHint);
        m.put("link_status", "EXPLICIT_REFERENCE"); m.put("resolution_rule", rule);
        out.add(stamp(m, new Lineage.Envelope("EventLink", id, null, null, null, null, "derived", canonTxn), ctx));
    }

    // ---- shared memoized source extractions ---------------------------------

    private List<AuditEvent> auditEvents(ExtractionContext ctx) {
        return ctx.memo("auditEvents", () -> new AuditEventService(ctx.session()).getAllAuditEvents());
    }

    private List<AccessRequest> accessRequests(ExtractionContext ctx) {
        return ctx.memo("accessRequests", () -> new AccessRequestService(ctx.session()).getAllRequests());
    }

    private List<ProvisioningTransaction> provTxns(ExtractionContext ctx) {
        return ctx.memo("provTxns", () -> new ProvisioningTransactionService(ctx.session()).getAllTransactions());
    }

    private List<Identity> identities(ExtractionContext ctx) {
        return ctx.memo("identities", () -> new IdentityService(ctx.api()).getAllIdentities());
    }

    private List<Account> accounts(ExtractionContext ctx) {
        return ctx.memo("accounts", () -> new AccountService(ctx.api()).getAllAccounts());
    }

    private List<Entitlement> entitlements(ExtractionContext ctx) {
        return ctx.memo("entitlements", () -> new EntitlementService(ctx.api()).getAllEntitlements());
    }

    // ---- small helpers -----------------------------------------------------

    private static Column col(String name, ParquetType type) {
        return Column.of(name, type);
    }

    private static Map<String, Object> stamp(Map<String, Object> business, Lineage.Envelope env, ExtractionContext ctx) {
        return Lineage.stamp(business, env, ctx.runId(), ctx.extractedAt());
    }

    private static Instant inst(LocalDateTime ldt) {
        return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
    }

    private static <T> Map<String, Set<String>> nameMap(List<T> items,
            java.util.function.Function<T, String> nameA, java.util.function.Function<T, String> nameB,
            java.util.function.Function<T, String> idFn) {
        Map<String, Set<String>> map = new HashMap<>();
        for (T t : items) {
            String id = idFn.apply(t);
            if (id == null) {
                continue;
            }
            addName(map, nameA.apply(t), id);
            addName(map, nameB.apply(t), id);
        }
        return map;
    }

    private static void addName(Map<String, Set<String>> map, String name, String id) {
        if (name != null && !name.isBlank()) {
            map.computeIfAbsent(name, k -> new HashSet<>()).add(id);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
