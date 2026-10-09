package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.keyforge.nativeload.NativeAccountEntitlementClient;
import com.keyforge.nativeload.NativeAccountEntitlementPageSource;
import com.keyforge.nativeload.NativeAccountEntitlementRestService;
import com.keyforge.nativeload.NativeAccountRestService;
import com.keyforge.nativeload.NativeApplicationClient;
import com.keyforge.nativeload.NativeApplicationPageSource;
import com.keyforge.nativeload.NativeAccessHistoryCertificationRestService;
import com.keyforge.nativeload.NativeAccessHistoryClient;
import com.keyforge.nativeload.NativeAccessHistoryEntitlementRestService;
import com.keyforge.nativeload.NativeAccessHistoryIdentityEventRestService;
import com.keyforge.nativeload.NativeApplicationRestService;
import com.keyforge.nativeload.NativeAuditEventClient;
import com.keyforge.nativeload.NativeAuditEventPageSource;
import com.keyforge.nativeload.NativeAuditEventRestService;
import com.keyforge.nativeload.NativeCertificationArchiveClient;
import com.keyforge.nativeload.NativeCertificationArchivePageSource;
import com.keyforge.nativeload.NativeCertificationArchiveRestService;
import com.keyforge.nativeload.NativeCertificationClient;
import com.keyforge.nativeload.NativeCertificationEntityClient;
import com.keyforge.nativeload.NativeCertificationEntityPageSource;
import com.keyforge.nativeload.NativeCertificationEntityRestService;
import com.keyforge.nativeload.NativeCertificationItemClient;
import com.keyforge.nativeload.NativeCertificationItemPageSource;
import com.keyforge.nativeload.NativeCertificationItemRestService;
import com.keyforge.nativeload.NativeCertificationPageSource;
import com.keyforge.nativeload.NativeCertificationRestService;
import com.keyforge.nativeload.NativeEntitlementRestService;
import com.keyforge.nativeload.NativeGroupDefinitionClient;
import com.keyforge.nativeload.NativeGroupDefinitionPageSource;
import com.keyforge.nativeload.NativeGroupDefinitionRestService;
import com.keyforge.nativeload.NativeLinkClient;
import com.keyforge.nativeload.NativeLinkPageSource;
import com.keyforge.nativeload.NativeIdentityClient;
import com.keyforge.nativeload.NativeIdentityPageSource;
import com.keyforge.nativeload.NativeIdentityEntitlementClient;
import com.keyforge.nativeload.NativeIdentityEntitlementPageSource;
import com.keyforge.nativeload.NativeIdentityEntitlementRestService;
import com.keyforge.nativeload.NativeIdentityRequestApprovalRestService;
import com.keyforge.nativeload.NativeIdentityRequestClient;
import com.keyforge.nativeload.NativeIdentityRequestItemRestService;
import com.keyforge.nativeload.NativeIdentityRequestPageSource;
import com.keyforge.nativeload.NativeIdentityRequestRestService;
import com.keyforge.nativeload.NativeIdentityRestService;
import com.keyforge.nativeload.NativeIdentityRoleClient;
import com.keyforge.nativeload.NativeIdentityRolePageSource;
import com.keyforge.nativeload.NativeIdentityRoleRestService;
import com.keyforge.nativeload.NativeManagedAttributeClient;
import com.keyforge.nativeload.NativeManagedAttributePageSource;
import com.keyforge.nativeload.NativePolicyClient;
import com.keyforge.nativeload.NativePolicyConstraintClient;
import com.keyforge.nativeload.NativePolicyConstraintPageSource;
import com.keyforge.nativeload.NativePolicyConstraintRestService;
import com.keyforge.nativeload.NativePolicyPageSource;
import com.keyforge.nativeload.NativePolicyRestService;
import com.keyforge.nativeload.NativeProvisioningItemRestService;
import com.keyforge.nativeload.NativeProvisioningTransactionRestService;
import com.keyforge.nativeload.NativeProvisioningTxnClient;
import com.keyforge.nativeload.NativeProvisioningTxnPageSource;
import com.keyforge.nativeload.NativeRoleClient;
import com.keyforge.nativeload.NativeRoleEntitlementRestService;
import com.keyforge.nativeload.NativeRoleHierarchyRestService;
import com.keyforge.nativeload.NativeRoleRelationshipClient;
import com.keyforge.nativeload.NativeRoleRelationshipPageSource;
import com.keyforge.nativeload.NativeRolePageSource;
import com.keyforge.nativeload.NativeRoleRestService;
import com.keyforge.nativeload.NativeSqlReportClient;
import com.keyforge.nativeload.NativeSqlReportRestService;
import com.keyforge.nativeload.NativeTaskResultClient;
import com.keyforge.nativeload.NativeTaskResultPageSource;
import com.keyforge.nativeload.NativeTaskResultRestService;
import com.keyforge.nativeload.NativeTaskScheduleClient;
import com.keyforge.nativeload.NativeTaskSchedulePageSource;
import com.keyforge.nativeload.NativeTaskScheduleRestService;
import com.keyforge.nativeload.NativeViolationClient;
import com.keyforge.nativeload.NativeViolationPageSource;
import com.keyforge.nativeload.NativeViolationRestService;
import com.keyforge.nativeload.NativeWorkItemArchiveClient;
import com.keyforge.nativeload.NativeWorkItemArchivePageSource;
import com.keyforge.nativeload.NativeWorkItemArchiveRestService;
import com.keyforge.nativeload.NativeWorkgroupMemberRestService;
import com.keyforge.nativeload.NativeWorkgroupMembershipClient;
import com.keyforge.nativeload.NativeWorkItemClient;
import com.keyforge.nativeload.NativeWorkItemPageSource;
import com.keyforge.nativeload.NativeWorkItemRestService;
import com.keyforge.nativeload.NativeWorkflowClient;
import com.keyforge.nativeload.NativeWorkflowDefinitionRestService;
import com.keyforge.nativeload.NativeWorkgroupClient;
import com.keyforge.nativeload.NativeWorkgroupPageSource;
import com.keyforge.nativeload.NativeWorkgroupRestService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * KF Agent REST service — exposes the existing native (Java-API) extraction as read-only JSON over HTTP,
 * so an external consumer (the Assurance Tool) can pull SailPoint data without any client-side database.
 * It reuses the existing native extraction/mapping verbatim and persists nothing: the DB write path is
 * bypassed here (see {@link NativeEntitlementRestService}). SCIM / {@code IiqApiClient} is NOT involved.
 *
 * <p>Resource-oriented endpoints (first module implemented): {@code GET /kfagent/entitlements}. Later
 * modules will be added as {@code /kfagent/identities}, {@code /kfagent/applications}, etc.
 *
 * <p>Default bind {@code 0.0.0.0:8100} (so it is reachable through an ngrok tunnel); override with env
 * {@code KFAGENT_HOST} / {@code KFAGENT_PORT} (falls back to {@code REST_PORT}, default 8100).
 */
public final class KfAgentRestServer {

    public static final String PREFIX = "/kfagent";
    public static final int DEFAULT_PORT = 8100;

    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final IiqSessionClient session;
    private final NativeEntitlementRestService entitlementService = new NativeEntitlementRestService();
    private final NativeIdentityRestService identityService = new NativeIdentityRestService();
    private final NativeApplicationRestService applicationService = new NativeApplicationRestService();
    private final NativeAccountRestService accountService = new NativeAccountRestService();
    private final NativeWorkgroupRestService workgroupService = new NativeWorkgroupRestService();
    private final NativeRoleRestService roleService = new NativeRoleRestService();
    private final NativeIdentityRoleRestService identityRoleService = new NativeIdentityRoleRestService();
    private final NativeIdentityEntitlementRestService identityEntitlementService = new NativeIdentityEntitlementRestService();
    private final NativeAccountEntitlementRestService accountEntitlementService = new NativeAccountEntitlementRestService();
    private final NativeRoleEntitlementRestService roleEntitlementService = new NativeRoleEntitlementRestService();
    private final NativeRoleHierarchyRestService roleHierarchyService = new NativeRoleHierarchyRestService();
    private final NativeGroupDefinitionRestService groupDefinitionService = new NativeGroupDefinitionRestService();
    private final NativeWorkItemArchiveRestService workItemArchiveService = new NativeWorkItemArchiveRestService();
    private final NativeCertificationArchiveRestService certificationArchiveService =
            new NativeCertificationArchiveRestService();
    private final NativeViolationRestService violationService = new NativeViolationRestService();
    private final NativeWorkgroupMemberRestService workgroupMemberService = new NativeWorkgroupMemberRestService();
    private final NativeIdentityRequestRestService identityRequestService = new NativeIdentityRequestRestService();
    private final NativeIdentityRequestItemRestService identityRequestItemService = new NativeIdentityRequestItemRestService();
    private final NativeIdentityRequestApprovalRestService identityRequestApprovalService = new NativeIdentityRequestApprovalRestService();
    private final NativePolicyRestService policyService = new NativePolicyRestService();
    private final NativeProvisioningItemRestService provisioningItemService = new NativeProvisioningItemRestService();
    private final NativeProvisioningTransactionRestService provisioningTransactionService =
            new NativeProvisioningTransactionRestService();
    private final NativeWorkItemRestService workItemService = new NativeWorkItemRestService();
    private final NativeWorkflowDefinitionRestService workflowDefinitionService =
            new NativeWorkflowDefinitionRestService();
    private final NativeCertificationRestService certificationService = new NativeCertificationRestService();
    private final NativeCertificationEntityRestService certificationEntityService =
            new NativeCertificationEntityRestService();
    private final NativeCertificationItemRestService certificationItemService =
            new NativeCertificationItemRestService();
    private final NativeAuditEventRestService auditEventService = new NativeAuditEventRestService();
    private final NativePolicyConstraintRestService policyConstraintService =
            new NativePolicyConstraintRestService();
    private final NativeTaskResultRestService taskResultService = new NativeTaskResultRestService();
    private final NativeTaskScheduleRestService taskScheduleService = new NativeTaskScheduleRestService();
    private final NativeAccessHistoryEntitlementRestService accessHistoryService =
            new NativeAccessHistoryEntitlementRestService();
    private final NativeAccessHistoryIdentityEventRestService accessHistoryIdentityEventService =
            new NativeAccessHistoryIdentityEventRestService();
    private final NativeAccessHistoryCertificationRestService accessHistoryCertificationService =
            new NativeAccessHistoryCertificationRestService();
    // Shared SQL-report read service. All four reports are registered for KF Agent REST: Query 1
    // (entitlementAssignment), Query 2 (workgroupMembers), Query 3 (entitlementCertification) and Query 4
    // (entitlementCertificationStatus). The SQL-report group is now complete.
    private final NativeSqlReportRestService sqlReportService =
            new NativeSqlReportRestService(java.util.Set.of(
                    "entitlementAssignment", "workgroupMembers", "entitlementCertification",
                    "entitlementCertificationStatus"));

    private HttpServer server;

    public KfAgentRestServer(AppConfig iiqConfig, String host, int port) {
        this.host = host;
        this.port = port;
        this.session = new IiqSessionClient(iiqConfig); // authenticates lazily, session reused across requests
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext(PREFIX, this::route);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("KF Agent REST listening on http://" + host + ":" + port + PREFIX
                + "/{entitlements,identities,applications,accounts,workgroups,roles,identity-roles,identity-entitlements,account-entitlements,role-entitlements,identity-requests,identity-request-items,identity-request-approvals,policies,provisioning-items,provisioning-transactions,work-items,workflow-definitions,certifications,certification-entities,certification-items,audit-events,policy-constraints,task-results,task-schedules,access-history,access-history/identity-events,access-history/certifications,role-hierarchy,group-definitions,workitem-archives,certification-archives,policy-violations,workgroupmember,entitlement-assignment,workgroup-members,entitlement-certification,entitlement-certification-status}  (read-only; no PostgreSQL on this path)");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, error("method not allowed; use GET"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            String sub = path.length() > PREFIX.length() ? path.substring(PREFIX.length()) : "";
            while (sub.startsWith("/")) {
                sub = sub.substring(1);
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());

            if ("entitlements".equals(sub)) {
                handleEntitlements(ex, q);
            } else if ("identities".equals(sub)) {
                handleIdentities(ex, q);
            } else if ("applications".equals(sub)) {
                handleApplications(ex, q);
            } else if ("accounts".equals(sub)) {
                handleAccounts(ex, q);
            } else if ("workgroups".equals(sub)) {
                handleWorkgroups(ex, q);
            } else if ("roles".equals(sub)) {
                handleRoles(ex, q);
            } else if ("identity-roles".equals(sub)) {
                handleIdentityRoles(ex, q);
            } else if ("identity-entitlements".equals(sub)) {
                handleIdentityEntitlements(ex, q);
            } else if ("account-entitlements".equals(sub)) {
                handleAccountEntitlements(ex, q);
            } else if ("role-entitlements".equals(sub)) {
                handleRoleEntitlements(ex, q);
            } else if ("role-hierarchy".equals(sub)) {
                handleRoleHierarchy(ex, q);
            } else if ("group-definitions".equals(sub)) {
                handleGroupDefinitions(ex, q);
            } else if ("workitem-archives".equals(sub)) {
                handleWorkItemArchives(ex, q);
            } else if ("certification-archives".equals(sub)) {
                handleCertificationArchives(ex, q);
            } else if ("policy-violations".equals(sub)) {
                handlePolicyViolations(ex, q);
            } else if ("workgroupmember".equals(sub)) {
                handleWorkgroupMembers(ex, q); // native Workgroup Member entity (Identity.getWorkgroups() edges)
            } else if ("entitlement-assignment".equals(sub)) {
                handleEntitlementAssignment(ex, q);
            } else if ("workgroup-members".equals(sub)) {
                handleWorkgroupMembersReport(ex, q); // SQL-report Query 2 (membership roster + user attrs/status)
            } else if ("entitlement-certification".equals(sub)) {
                handleEntitlementCertification(ex, q); // SQL-report Query 3 (entitlement + never-certified flag)
            } else if ("entitlement-certification-status".equals(sub)) {
                handleEntitlementCertificationStatus(ex, q); // SQL-report Query 4 (entitlement + latest cert status)
            } else if ("identity-requests".equals(sub)) {
                handleIdentityRequests(ex, q);
            } else if ("identity-request-items".equals(sub)) {
                handleIdentityRequestItems(ex, q);
            } else if ("identity-request-approvals".equals(sub)) {
                handleIdentityRequestApprovals(ex, q);
            } else if ("policies".equals(sub)) {
                handlePolicies(ex, q);
            } else if ("provisioning-items".equals(sub)) {
                handleProvisioningItems(ex, q);
            } else if ("provisioning-transactions".equals(sub)) {
                handleProvisioningTransactions(ex, q);
            } else if ("work-items".equals(sub)) {
                handleWorkItems(ex, q);
            } else if ("workflow-definitions".equals(sub)) {
                handleWorkflowDefinitions(ex, q);
            } else if ("certifications".equals(sub)) {
                handleCertifications(ex, q);
            } else if ("certification-entities".equals(sub)) {
                handleCertificationEntities(ex, q);
            } else if ("certification-items".equals(sub)) {
                handleCertificationItems(ex, q);
            } else if ("audit-events".equals(sub)) {
                handleAuditEvents(ex, q);
            } else if ("policy-constraints".equals(sub)) {
                handlePolicyConstraints(ex, q);
            } else if ("task-results".equals(sub)) {
                handleTaskResults(ex, q);
            } else if ("task-schedules".equals(sub)) {
                handleTaskSchedules(ex, q);
            } else if ("access-history/identity-events".equals(sub)) {
                handleAccessHistoryIdentityEvents(ex, q);
            } else if ("access-history/certifications".equals(sub)) {
                handleAccessHistoryCertifications(ex, q);
            } else if ("access-history".equals(sub)) {
                handleAccessHistory(ex, q);
            } else {
                writeJson(ex, 404, error("unknown resource '" + sub
                        + "' — implemented: 'entitlements', 'identities', 'applications', 'accounts', 'workgroups', 'roles', 'identity-roles', 'identity-entitlements', 'account-entitlements', 'role-entitlements', 'identity-requests', 'identity-request-items', 'identity-request-approvals', 'policies', 'provisioning-items', 'provisioning-transactions', 'work-items', 'workflow-definitions', 'certifications', 'certification-entities', 'certification-items', 'audit-events', 'policy-constraints', 'task-results', 'task-schedules', 'access-history', 'access-history/identity-events', 'access-history/certifications', 'role-hierarchy', 'group-definitions', 'workitem-archives', 'certification-archives', 'policy-violations', 'workgroupmember', 'entitlement-assignment', 'workgroup-members', 'entitlement-certification', 'entitlement-certification-status'"));
            }
        } catch (Exception e) {
            writeJson(ex, 500, error(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())));
        }
    }

    /** Reserved query params (paging + the one native server-side bound); everything else is a field filter. */
    private static final java.util.Set<String> RESERVED =
            java.util.Set.of("start", "limit", "modifiedAfter");

    private void handleEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        // Generic filtering: every non-reserved query param is an exact filter on the response field of
        // the same name (our DB/response field names, e.g. source_id, value, type, is_group_type).
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // modifiedAfter is the ONE native server-side bound; applied by building the page source with it.
        NativeManagedAttributeClient client = new NativeManagedAttributeClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeManagedAttributePageSource source = client;

        try {
            List<Map<String, Object>> rows = entitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentities(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeIdentityClient client = new NativeIdentityClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeIdentityPageSource source = client;

        try {
            List<Map<String, Object>> rows = identityService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleApplications(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeApplicationClient client = new NativeApplicationClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeApplicationPageSource source = client;

        try {
            List<Map<String, Object>> rows = applicationService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccounts(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeLinkClient client = new NativeLinkClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeLinkPageSource source = client;

        try {
            List<Map<String, Object>> rows = accountService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkgroups(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Workgroup client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeWorkgroupPageSource source = new NativeWorkgroupClient(session);

        try {
            List<Map<String, Object>> rows = workgroupService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleRoles(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeRoleClient client = new NativeRoleClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeRolePageSource source = client;

        try {
            List<Map<String, Object>> rows = roleService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRoles(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-role client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityRolePageSource source = new NativeIdentityRoleClient(session);

        try {
            List<Map<String, Object>> rows = identityRoleService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-entitlement client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityEntitlementPageSource source = new NativeIdentityEntitlementClient(session);

        try {
            List<Map<String, Object>> rows = identityEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccountEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native account-entitlement client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeAccountEntitlementPageSource source = new NativeAccountEntitlementClient(session);

        try {
            List<Map<String, Object>> rows = accountEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleRoleEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Bundle-relationship client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeRoleRelationshipPageSource source = new NativeRoleRelationshipClient(session);

        try {
            List<Map<String, Object>> rows = roleEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleRoleHierarchy(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Same shared Bundle-relationship extraction as role-entitlements; this endpoint collects only the
        // hierarchy edges. No server-side modifiedAfter support (full-scan only); modifiedAfter stays reserved.
        NativeRoleRelationshipPageSource source = new NativeRoleRelationshipClient(session);

        try {
            List<Map<String, Object>> rows = roleHierarchyService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleGroupDefinitions(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native GroupDefinition extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeGroupDefinitionPageSource source = new NativeGroupDefinitionClient(session);

        try {
            List<Map<String, Object>> rows = groupDefinitionService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkItemArchives(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native WorkItemArchive extraction (append-only CEC history). The service collects records
        // in memory (no DB). No modifiedAfter support on the client (full-scan only); modifiedAfter reserved.
        NativeWorkItemArchivePageSource source = new NativeWorkItemArchiveClient(session);

        try {
            List<Map<String, Object>> rows = workItemArchiveService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleCertificationArchives(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native CertificationArchive extraction (append-only CEC history). The service collects
        // records in memory (no DB). No modifiedAfter support (full-scan only); modifiedAfter reserved.
        NativeCertificationArchivePageSource source = new NativeCertificationArchiveClient(session);

        try {
            List<Map<String, Object>> rows = certificationArchiveService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handlePolicyViolations(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native PolicyViolation extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeViolationPageSource source = new NativeViolationClient(session);

        try {
            List<Map<String, Object>> rows = violationService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkgroupMembers(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Reuse the existing native Workgroup-Membership client + parser (the import service is DB-coupled, so
        // it is not used here); runId is a plugin lineage query param, not a DB handle. No modifiedAfter
        // support (full-scan only); modifiedAfter stays reserved. No PostgreSQL on this path.
        NativeWorkgroupMembershipClient client =
                new NativeWorkgroupMembershipClient(session, java.util.UUID.randomUUID().toString());

        try {
            List<Map<String, Object>> rows =
                    workgroupMemberService.fetch(client::fetch, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    /**
     * SQL-report Query 1 (Entitlement Assignment). Reuses the existing {@link NativeSqlReportClient} to
     * pull the baked server-side report envelope from the IIQ plugin over the authenticated session, and
     * the shared {@link NativeSqlReportRestService} to filter/page/serialize it. The report id is fixed
     * ("entitlementAssignment") — never caller-supplied — so this is not an arbitrary-SQL surface. No
     * PostgreSQL on this path; the existing extract-native-entitlement-assignment-db command is untouched.
     * No modifiedAfter (the report client runs the whole SELECT); it stays reserved.
     */
    private void handleEntitlementAssignment(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(client::fetch, "entitlementAssignment", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        }
    }

    /**
     * SQL-report Query 2 (Workgroup Members). A membership <i>roster</i> with user display/profile
     * attributes and a CASE-derived status — distinct from the native Workgroup Member entity (served at
     * {@code /kfagent/workgroupmember}), which is the {@code Identity.getWorkgroups()} edge topology.
     * Reuses the same shared {@link NativeSqlReportRestService} + {@link NativeSqlReportClient}; report id
     * is fixed ("workgroupMembers"), never caller-supplied. No PostgreSQL on this path; the existing
     * extract-native-workgroup-members-db command is untouched. No modifiedAfter (whole SELECT), reserved.
     */
    private void handleWorkgroupMembersReport(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(client::fetch, "workgroupMembers", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        }
    }

    /**
     * SQL-report Query 3 (Entitlement Certification): every identity entitlement plus a
     * {@code never_certified} flag ('YES'/'NO'). Reuses the same shared {@link NativeSqlReportRestService}
     * + {@link NativeSqlReportClient}; report id is fixed ("entitlementCertification"), never
     * caller-supplied. No PostgreSQL on this path; the existing extract-native-entitlement-certification-db
     * command is untouched. No modifiedAfter (whole SELECT), reserved.
     */
    private void handleEntitlementCertification(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(client::fetch, "entitlementCertification", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        }
    }

    /**
     * SQL-report Query 4 (Entitlement Certification Status): every identity entitlement plus its latest
     * certification info (name/decision/certifier/decision-date from the {@code certification_history} CTE)
     * and a {@code never_certified} flag. Only the 14 outer-SELECT columns are returned; the CTE-internal
     * {@code rn}/{@code certification_id} are not projected. Reuses the same shared
     * {@link NativeSqlReportRestService} + {@link NativeSqlReportClient}; report id is fixed
     * ("entitlementCertificationStatus"), never caller-supplied. No PostgreSQL on this path; the existing
     * extract-native-entitlement-certification-status-db command is untouched. No modifiedAfter, reserved.
     */
    private void handleEntitlementCertificationStatus(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(client::fetch, "entitlementCertificationStatus", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        }
    }

    private void handleIdentityRequests(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-request client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRequestItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Items are extracted by the SHARED IdentityRequest native import; the item service collects only
        // the item rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved/ignored.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRequestApprovals(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Approvals are extracted by the SHARED IdentityRequest native import; the approval service collects
        // only the approval rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestApprovalService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handlePolicies(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Policy client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativePolicyPageSource source = new NativePolicyClient(session);

        try {
            List<Map<String, Object>> rows = policyService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleProvisioningItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Items are derived by the SHARED ProvisioningTransaction native import; the item service collects
        // only the item rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved.
        NativeProvisioningTxnPageSource source = new NativeProvisioningTxnClient(session);

        try {
            List<Map<String, Object>> rows = provisioningItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleProvisioningTransactions(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Same SHARED native ProvisioningTransaction extraction as the DB path; the txn service collects only the
        // transaction rows (derived items untouched). No modifiedAfter support (full-scan); modifiedAfter stays reserved.
        NativeProvisioningTxnPageSource source = new NativeProvisioningTxnClient(session);

        try {
            List<Map<String, Object>> rows = provisioningTransactionService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native WorkItem extraction; the service collects records in memory (no DB). No modifiedAfter
        // support on the WorkItem client (full-scan only); modifiedAfter stays reserved.
        NativeWorkItemPageSource source = new NativeWorkItemClient(session);

        try {
            List<Map<String, Object>> rows = workItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkflowDefinitions(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Reuse the existing native Workflow client + parser (the import service is DB-coupled, so it is not
        // used here); runId is a plugin lineage query param, not a DB handle. No modifiedAfter support
        // (full-scan only); modifiedAfter stays reserved. No PostgreSQL on this path.
        NativeWorkflowClient client = new NativeWorkflowClient(session, java.util.UUID.randomUUID().toString());

        try {
            List<Map<String, Object>> rows = workflowDefinitionService.fetch(client::fetch, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleCertifications(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native Certification extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the certification client (full-scan only); modifiedAfter stays reserved.
        NativeCertificationPageSource source = new NativeCertificationClient(session);

        try {
            List<Map<String, Object>> rows = certificationService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleCertificationEntities(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native CertificationEntity extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeCertificationEntityPageSource source = new NativeCertificationEntityClient(session);

        try {
            List<Map<String, Object>> rows = certificationEntityService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleCertificationItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native CertificationItem extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeCertificationItemPageSource source = new NativeCertificationItemClient(session);

        try {
            List<Map<String, Object>> rows = certificationItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAuditEvents(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native AuditEvent extraction (append-only); the service collects records in memory (no DB).
        // No modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeAuditEventPageSource source = new NativeAuditEventClient(session);

        try {
            List<Map<String, Object>> rows = auditEventService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handlePolicyConstraints(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native PolicyConstraint extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativePolicyConstraintPageSource source = new NativePolicyConstraintClient(session);

        try {
            List<Map<String, Object>> rows = policyConstraintService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleTaskResults(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native TaskResult extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeTaskResultPageSource source = new NativeTaskResultClient(session);

        try {
            List<Map<String, Object>> rows = taskResultService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleTaskSchedules(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native TaskSchedule extraction; the service collects records in memory (no DB). No
        // modifiedAfter support on the client (full-scan only); modifiedAfter stays reserved.
        NativeTaskSchedulePageSource source = new NativeTaskScheduleClient(session);

        try {
            List<Map<String, Object>> rows = taskScheduleService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccessHistory(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native Access-History entitlement-capture extraction (append-only, immutable). The
        // import service is DB-coupled, so it is not used here; the client + envelope are reused directly and
        // the service collects rows in memory (no DB). No modifiedAfter support; it stays reserved.
        NativeAccessHistoryClient client = new NativeAccessHistoryClient(session);

        try {
            List<Map<String, Object>> rows = accessHistoryService.fetch(
                    (s, l) -> client.fetchPage(NativeAccessHistoryClient.ENTITLEMENT_CAPTURES_PATH, s, l),
                    filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccessHistoryIdentityEvents(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native Access-History identity-event extraction (append-only). DB-coupled import is not
        // used; the client + envelope are reused directly (identity-events path, NOT entitlement-captures).
        NativeAccessHistoryClient client = new NativeAccessHistoryClient(session);

        try {
            List<Map<String, Object>> rows = accessHistoryIdentityEventService.fetch(
                    (s, l) -> client.fetchPage(NativeAccessHistoryClient.IDENTITY_EVENTS_PATH, s, l),
                    filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccessHistoryCertifications(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Existing native Access-History certification extraction (append-only). DB-coupled import is not
        // used; the client + envelope are reused directly (certifications path). Table is currently empty →
        // live response is []; it returns real rows automatically once certification history exists.
        NativeAccessHistoryClient client = new NativeAccessHistoryClient(session);

        try {
            List<Map<String, Object>> rows = accessHistoryCertificationService.fetch(
                    (s, l) -> client.fetchPage(NativeAccessHistoryClient.CERTIFICATIONS_PATH, s, l),
                    filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("service", "kfagent");
        writeJson(ex, 200, mapper.writeValueAsBytes(ok));
    }

    // --- helpers ------------------------------------------------------------

    private static Integer intOrNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return out;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(decode(pair), "");
            } else {
                out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return out;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private byte[] error(String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("error", message);
        try {
            return mapper.writeValueAsBytes(e);
        } catch (Exception ex) {
            return ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private void writeJson(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
