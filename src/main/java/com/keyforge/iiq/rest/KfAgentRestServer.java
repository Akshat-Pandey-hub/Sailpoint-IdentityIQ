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
import com.keyforge.nativeload.NativeAccessHistoryRoleEventRestService;
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
import com.keyforge.nativeload.KfAgentScanEvidence;
import com.keyforge.nativeload.NativeSyslogEventClient;
import com.keyforge.nativeload.NativeSyslogEventPageSource;
import com.keyforge.nativeload.NativeSyslogEventRestService;
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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KfAgentRestServer.class);

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
    private final NativeAccessHistoryRoleEventRestService accessHistoryRoleEventService =
            new NativeAccessHistoryRoleEventRestService();
    private final NativeAccessHistoryCertificationRestService accessHistoryCertificationService =
            new NativeAccessHistoryCertificationRestService();
    // Shared SQL-report read service. All four reports are registered for KF Agent REST: Query 1
    // (entitlementAssignment), Query 2 (workgroupMembers), Query 3 (entitlementCertification) and Query 4
    // (entitlementCertificationStatus). The SQL-report group is now complete.
    private final NativeSqlReportRestService sqlReportService =
            new NativeSqlReportRestService(java.util.Set.of(
                    "entitlementAssignment", "workgroupMembers", "entitlementCertification",
                    "entitlementCertificationStatus"));
    private final NativeSyslogEventRestService syslogEventService = new NativeSyslogEventRestService();

    private final RestAuthFilter auth;
    private HttpServer server;

    public KfAgentRestServer(AppConfig iiqConfig, String host, int port) {
        this(iiqConfig, host, port, RestAuthFilter.fromEnvironment());
    }

    /**
     * @param iiqConfig the IdentityIQ login (IIQ_USERNAME / IIQ_PASSWORD), used only for this server's own session
     * @param auth the REST API Basic-auth guard applied to every context this server registers (by default the
     *             client ID / secret in KFAGENT_USERNAME / KFAGENT_PASSWORD)
     */
    public KfAgentRestServer(AppConfig iiqConfig, String host, int port, RestAuthFilter auth) {
        this.host = host;
        this.port = port;
        this.session = new IiqSessionClient(iiqConfig); // authenticates lazily, session reused across requests
        this.auth = auth;
    }

    public RestAuthFilter auth() {
        return auth;
    }

    /**
     * Registers this service's routes ({@code /health} and {@code /kfagent/*}) onto a caller-owned
     * {@link HttpServer}. Lets KF Agent be co-hosted with another service on a SINGLE port (one server,
     * many contexts) — the combined {@code start-kfagent} flow uses this to serve KF Agent and the Parquet
     * query service on the same port. Does not create, configure the executor of, start or stop the server;
     * the caller owns its lifecycle.
     */
    public void registerInto(HttpServer target) {
        auth.protect(target.createContext("/health", this::handleHealth));
        auth.protect(target.createContext(PREFIX, this::route));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        registerInto(server);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        LOG.info("KF Agent REST listening on http://" + host + ":" + port + PREFIX
                + "/{entitlements,identities,applications,accounts,workgroups,roles,identity-roles,identity-entitlements,account-entitlements,role-entitlements,identity-requests,identity-request-items,identity-request-approvals,policies,provisioning-items,provisioning-transactions,work-items,workflow-definitions,certifications,certification-entities,certification-items,audit-events,policy-constraints,task-results,task-schedules,access-history,access-history/identity-events,access-history/role-events,access-history/certifications,role-hierarchy,group-definitions,workitem-archives,certification-archives,policy-violations,workgroupmember,entitlement-assignment,workgroup-members,entitlement-certification,entitlement-certification-status,syslog-events}  (read-only; no PostgreSQL on this path)");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        KfAgentScanEvidence.clear(); // request-scoped completeness evidence (see writeJson)
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
            Map<String, String> q;
            try {
                q = parseQuery(ex.getRequestURI().getRawQuery());
            } catch (IllegalArgumentException badEncoding) {
                writeJson(ex, 400, error("malformed query string: " + badEncoding.getMessage()));
                return;
            }
            if (com.keyforge.iiq.debug.DebugLog.enabled()) {
                com.keyforge.iiq.debug.DebugLog.log("SERVICE", "endpoint", "/kfagent/" + sub,
                        "purpose", KfAgentServiceCatalog.purpose(sub),
                        "source", "IdentityIQ via the KeyForge plugin (read-only)",
                        "start", q.get("start"), "limit", q.get("limit"),
                        "IIQ_BASE_URL", com.keyforge.iiq.debug.DebugLog.setting("IIQ_BASE_URL"),
                        "IIQ_USERNAME", com.keyforge.iiq.debug.DebugLog.setting("IIQ_USERNAME"),
                        "IIQ_PASSWORD", com.keyforge.iiq.debug.DebugLog.setting("IIQ_PASSWORD"));
            }
            String invalid = invalidReservedParam(q);
            if (invalid != null) {
                writeJson(ex, 400, error(invalid));
                return;
            }

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
            } else if ("syslog-events".equals(sub)) {
                handleSyslogEvents(ex, q); // native SyslogEvent (IIQ operational/diagnostic log)
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
            } else if ("access-history/role-events".equals(sub)) {
                handleAccessHistoryRoleEvents(ex, q);
            } else if ("access-history/certifications".equals(sub)) {
                handleAccessHistoryCertifications(ex, q);
            } else if ("access-history".equals(sub)) {
                handleAccessHistory(ex, q);
            } else {
                writeJson(ex, 404, error("unknown resource '" + sub
                        + "' — implemented: 'entitlements', 'identities', 'applications', 'accounts', 'workgroups', 'roles', 'identity-roles', 'identity-entitlements', 'account-entitlements', 'role-entitlements', 'identity-requests', 'identity-request-items', 'identity-request-approvals', 'policies', 'provisioning-items', 'provisioning-transactions', 'work-items', 'workflow-definitions', 'certifications', 'certification-entities', 'certification-items', 'audit-events', 'policy-constraints', 'task-results', 'task-schedules', 'access-history', 'access-history/identity-events', 'access-history/role-events', 'access-history/certifications', 'role-hierarchy', 'group-definitions', 'workitem-archives', 'certification-archives', 'policy-violations', 'workgroupmember', 'entitlement-assignment', 'workgroup-members', 'entitlement-certification', 'entitlement-certification-status', 'syslog-events'"));
            }
        } catch (Exception e) {
            // generic message to the caller; the details stay in the server log (never credentials/headers)
            com.keyforge.iiq.debug.DebugLog.failure(LOG, ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + " failed; returned HTTP 500", e, "query/filters", ex.getRequestURI().getRawQuery());
            writeJson(ex, 500, error("Internal Server Error"));
        } catch (Error fatal) {
            // e.g. OutOfMemoryError on an oversized request: answer this caller with a JSON 500 (best effort)
            // instead of dropping the connection; the worker pool and the server keep serving other requests.
            com.keyforge.iiq.debug.DebugLog.failure(LOG, ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + " failed; returned HTTP 500", fatal, "query/filters", ex.getRequestURI().getRawQuery());
            try {
                writeJson(ex, 500, error("Internal Server Error"));
            } catch (Throwable ignored) {
                // the response could not be written (e.g. headers already sent); nothing more to do
            }
        } finally {
            KfAgentScanEvidence.clear();
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

        // optional incremental bound (changed after); absent = full population
        NativeWorkgroupPageSource source = new NativeWorkgroupClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // optional incremental bound (changed after); absent = full population
        NativeIdentityEntitlementPageSource source = new NativeIdentityEntitlementClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // optional incremental bound on the ACCOUNT's (Link's) change time: edges of accounts changed after it
        NativeAccountEntitlementPageSource source = new NativeAccountEntitlementClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // Existing native GroupDefinition extraction; modifiedAfter is passed through to the plugin.
        NativeGroupDefinitionPageSource source = new NativeGroupDefinitionClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (created after); absent = full population
        NativeWorkItemArchivePageSource source = new NativeWorkItemArchiveClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (created after); absent = full population
        NativeCertificationArchivePageSource source = new NativeCertificationArchiveClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // Existing native PolicyViolation extraction; modifiedAfter is passed through to the plugin.
        NativeViolationPageSource source = new NativeViolationClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        Integer limit = reportLimit(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(id -> client.fetch(id, filters, start, limit),
                            "entitlementAssignment", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        } catch (com.keyforge.nativeload.NativeImportException incomplete) {
            writeJson(ex, 502, error(incomplete.getMessage()));
        } catch (com.keyforge.iiq.client.IiqApiException iiq) {
            if (iiq.getStatusCode() != 400) {
                throw iiq; // any other IIQ failure stays a server error, as before
            }
            // the plugin rejected the request (unknown filter field or invalid start/limit)
            writeJson(ex, 400, error("invalid report request: unknown filter field or invalid start/limit"));
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
        Integer limit = reportLimit(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(id -> client.fetch(id, filters, start, limit),
                            "workgroupMembers", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        } catch (com.keyforge.nativeload.NativeImportException incomplete) {
            writeJson(ex, 502, error(incomplete.getMessage()));
        } catch (com.keyforge.iiq.client.IiqApiException iiq) {
            if (iiq.getStatusCode() != 400) {
                throw iiq; // any other IIQ failure stays a server error, as before
            }
            // the plugin rejected the request (unknown filter field or invalid start/limit)
            writeJson(ex, 400, error("invalid report request: unknown filter field or invalid start/limit"));
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
        Integer limit = reportLimit(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(id -> client.fetch(id, filters, start, limit),
                            "entitlementCertification", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        } catch (com.keyforge.nativeload.NativeImportException incomplete) {
            writeJson(ex, 502, error(incomplete.getMessage()));
        } catch (com.keyforge.iiq.client.IiqApiException iiq) {
            if (iiq.getStatusCode() != 400) {
                throw iiq; // any other IIQ failure stays a server error, as before
            }
            // the plugin rejected the request (unknown filter field or invalid start/limit)
            writeJson(ex, 400, error("invalid report request: unknown filter field or invalid start/limit"));
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
        Integer limit = reportLimit(q.get("limit"));

        NativeSqlReportClient client = new NativeSqlReportClient(session);
        try {
            List<Map<String, Object>> rows =
                    sqlReportService.fetch(id -> client.fetch(id, filters, start, limit),
                            "entitlementCertificationStatus", filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field / unregistered report
        } catch (com.keyforge.nativeload.NativeImportException incomplete) {
            writeJson(ex, 502, error(incomplete.getMessage()));
        } catch (com.keyforge.iiq.client.IiqApiException iiq) {
            if (iiq.getStatusCode() != 400) {
                throw iiq; // any other IIQ failure stays a server error, as before
            }
            // the plugin rejected the request (unknown filter field or invalid start/limit)
            writeJson(ex, 400, error("invalid report request: unknown filter field or invalid start/limit"));
        }
    }

    /**
     * Native SyslogEvent (IIQ's own immutable operational/diagnostic log — server-side logging events,
     * distinct from AuditEvent). Reuses the EXISTING native {@link NativeSyslogEventClient} page source +
     * import (append-only); the service collects records in memory (no DB). No modifiedAfter support on the
     * client (full-scan only); modifiedAfter stays reserved. No PostgreSQL on this path. An unfiltered request
     * with a limit reads only its window from the plugin (no full scan per page); the plugin's source count is
     * returned in the {@value NativeSyslogEventRestService#SOURCE_COUNT_HEADER} header for completeness checks.
     */
    private void handleSyslogEvents(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // optional incremental bound (created after); absent = full population
        NativeSyslogEventPageSource source = new NativeSyslogEventClient(session).withModifiedAfter(q.get("modifiedAfter"));
        try {
            NativeSyslogEventRestService.Page page = syslogEventService.fetchPage(source, filters, start, limit);
            if (page.sourceCount() >= 0) {
                ex.getResponseHeaders().set(NativeSyslogEventRestService.SOURCE_COUNT_HEADER,
                        Integer.toString(page.sourceCount()));
            }
            writeJson(ex, 200, mapper.writeValueAsBytes(page.rows())); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
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

        // optional incremental bound (changed after); absent = full population
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound on the PARENT's change time (children of parents changed after it)
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound on the PARENT's change time (children of parents changed after it)
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // optional incremental bound (changed after); absent = full population
        NativePolicyPageSource source = new NativePolicyClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound on the PARENT's change time (children of parents changed after it)
        NativeProvisioningTxnPageSource source = new NativeProvisioningTxnClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (changed after); absent = full population
        NativeProvisioningTxnPageSource source = new NativeProvisioningTxnClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (changed after); absent = full population
        NativeWorkItemPageSource source = new NativeWorkItemClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // Reuse the existing native Workflow client + parser; runId is a plugin lineage query param.
        // No PostgreSQL on this path; modifiedAfter is passed through to the plugin.
        NativeWorkflowClient client = new NativeWorkflowClient(session, java.util.UUID.randomUUID().toString()).withModifiedAfter(q.get("modifiedAfter"));

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

        // Existing native Certification extraction; modifiedAfter is passed through to the plugin.
        NativeCertificationPageSource source = new NativeCertificationClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (changed after); absent = full population
        NativeCertificationEntityPageSource source = new NativeCertificationEntityClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (changed after); absent = full population
        NativeCertificationItemPageSource source = new NativeCertificationItemClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (created after); absent = full population
        NativeAuditEventPageSource source = new NativeAuditEventClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (changed after); absent = full population
        NativeTaskResultPageSource source = new NativeTaskResultClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

        // Existing native TaskSchedule extraction; modifiedAfter is passed through to the plugin.
        NativeTaskSchedulePageSource source = new NativeTaskScheduleClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (created after); absent = full population
        NativeAccessHistoryClient client =
                new NativeAccessHistoryClient(session).withModifiedAfter(q.get("modifiedAfter"));

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
        // optional incremental bound (created after); absent = full population
        NativeAccessHistoryClient client =
                new NativeAccessHistoryClient(session).withModifiedAfter(q.get("modifiedAfter"));

        try {
            List<Map<String, Object>> rows = accessHistoryIdentityEventService.fetch(
                    (s, l) -> client.fetchPage(NativeAccessHistoryClient.IDENTITY_EVENTS_PATH, s, l),
                    filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    /**
     * Native Access-History role events ({@code sailpoint.object.accesshistory.HistoricalRoleEvent}) — the
     * role-centric change history. Same architecture as the identity-event route: reuses the DB-free
     * {@link NativeAccessHistoryClient} + envelope (role-events path), no DB-coupled import, no PostgreSQL.
     */
    private void handleAccessHistoryRoleEvents(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // optional incremental bound (created after); absent = full population
        NativeAccessHistoryClient client =
                new NativeAccessHistoryClient(session).withModifiedAfter(q.get("modifiedAfter"));
        try {
            List<Map<String, Object>> rows = accessHistoryRoleEventService.fetch(
                    (s, l) -> client.fetchPage(NativeAccessHistoryClient.ROLE_EVENTS_PATH, s, l),
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
        // optional incremental bound (created after); absent = full population
        NativeAccessHistoryClient client =
                new NativeAccessHistoryClient(session).withModifiedAfter(q.get("modifiedAfter"));

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

    /**
     * Validates the reserved params before any extraction: a present {@code start}/{@code limit} must be an
     * integer, and a present {@code modifiedAfter} must be an ISO-8601 instant or offset date-time (exactly the
     * formats the plugin's {@code NativeIncrementalQuery.parseIsoToDate} accepts). Without this a typo was
     * silently ignored: a bad limit returned everything, and a bad modifiedAfter collapsed to a full scan.
     *
     * @return a client-facing error message, or {@code null} when the params are valid (or absent)
     */
    static String invalidReservedParam(Map<String, String> q) {
        for (String key : new String[]{"start", "limit"}) {
            String v = q.get(key);
            if (v != null && !v.trim().isEmpty()) {
                try {
                    Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    return "invalid '" + key + "': '" + v + "' is not an integer";
                }
            }
        }
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            String s = modifiedAfter.trim();
            try {
                java.time.Instant.parse(s);
            } catch (java.time.format.DateTimeParseException notInstant) {
                try {
                    java.time.OffsetDateTime.parse(s);
                } catch (java.time.format.DateTimeParseException notOffset) {
                    return "invalid 'modifiedAfter': '" + modifiedAfter
                            + "' is not an ISO-8601 instant (e.g. 2026-10-12T10:00:00Z, or 2026-10-12T15:30:00%2B05:30 with '+' URL-encoded)";
                }
            }
        }
        return null;
    }

    /** SQL-report limit: a negative value means "no limit", as on every other endpoint. */
    private static Integer reportLimit(String raw) {
        Integer limit = intOrNull(raw);
        return limit != null && limit < 0 ? null : limit;
    }

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
        Integer verified = KfAgentScanEvidence.current();
        if (status == 200 && verified != null) {
            // this body came from ONE full internal scan whose complete-scan guard passed against this IIQ count
            ex.getResponseHeaders().set(KfAgentScanEvidence.HEADER, Integer.toString(verified));
        }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
        logResult(ex, status, body);
    }

    /**
     * RESULT line: status, rows returned (for a JSON array) or the error message sent to the caller. A 4xx reply
     * (bad filter, unknown resource, wrong method) is a WARN written at every level, like a 401.
     */
    private void logResult(HttpExchange ex, int status, byte[] body) {
        boolean rejected = status >= 400 && status < 500;
        if (!rejected && !com.keyforge.iiq.debug.DebugLog.enabled()) {
            return;
        }
        Object detail;
        try {
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
            if (node.isArray()) {
                detail = node.size() + " rows";
            } else if (node.has("error")) {
                detail = "error: " + node.get("error").asText();
            } else {
                detail = "object";
            }
        } catch (Exception notJson) {
            detail = "-";
        }
        if (rejected) {
            com.keyforge.iiq.debug.DebugLog.warn("RESULT", "endpoint", ex.getRequestURI().getPath(), "status", status,
                    "returned", detail, "ms", com.keyforge.iiq.debug.DebugLog.elapsedMs());
            return;
        }
        com.keyforge.iiq.debug.DebugLog.log("RESULT", "endpoint", ex.getRequestURI().getPath(), "status", status,
                "returned", detail, "bytes", body.length, "ms", com.keyforge.iiq.debug.DebugLog.elapsedMs());
    }
}
