package com.keyforge.iiq;

import com.keyforge.iiq.application.ApplicationService;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.account.AccountPersistenceService;
import com.keyforge.iiq.account.AccountService;
import com.keyforge.iiq.application.ApplicationInstancePersistenceService;
import com.keyforge.iiq.application.ApplicationPersistenceService;
import com.keyforge.iiq.assignment.AccountEntitlementAssignmentService;
import com.keyforge.iiq.assignment.EntitlementAssignmentPersistenceService;
import com.keyforge.iiq.catalog.CatalogPersistenceService;
import com.keyforge.iiq.config.ConfigException;
import com.keyforge.iiq.config.PgConfig;
import com.keyforge.iiq.db.PostgresConnection;
import com.keyforge.iiq.entitlement.EntitlementPersistenceService;
import com.keyforge.iiq.entitlement.EntitlementService;
import com.keyforge.iiq.identity.IdentityService;
import com.keyforge.iiq.model.Account;
import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.Application;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Identity;
import com.keyforge.iiq.model.UserGroup;
import com.keyforge.iiq.user.UserPersistenceService;
import com.keyforge.iiq.usergroup.UserGroupPersistenceService;
import com.keyforge.iiq.usergroup.UserGroupService;
import com.keyforge.iiq.model.WorkItem;
import com.keyforge.iiq.workitem.WorkItemPersistenceService;
import com.keyforge.iiq.workitem.WorkItemService;
import com.keyforge.iiq.model.Role;
import com.keyforge.iiq.role.RoleService;
import com.keyforge.iiq.role.RoleHierarchyRowMapper;
import com.keyforge.iiq.role.RolePersistenceService;
import com.keyforge.iiq.role.RoleHierarchyPersistenceService;
import com.keyforge.iiq.workgroup.WorkgroupPersistenceService;
import com.keyforge.iiq.workgroup.WorkgroupRow;
import com.keyforge.iiq.workgroup.WorkgroupRowMapper;
import com.keyforge.iiq.objectowner.ObjectOwnerPersistenceService;
import com.keyforge.iiq.objectowner.ObjectOwnerRow;
import com.keyforge.iiq.accountentitlement.AccountEntitlementPersistenceService;
import com.keyforge.iiq.accountentitlement.AccountEntitlementRow;
import com.keyforge.iiq.accountentitlement.AccountEntitlementRowMapper;
import com.keyforge.iiq.identityentitlement.IdentityEntitlementPersistenceService;
import com.keyforge.iiq.identityentitlement.IdentityEntitlementRow;
import com.keyforge.iiq.identityrole.IdentityRoleAssignment;
import com.keyforge.iiq.identityrole.IdentityRoleService;
import com.keyforge.iiq.identityrole.IdentityRolePersistenceService;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.nativeparquet.NativeParquetService;
import com.keyforge.iiq.parquet.ParquetExtractionService;
import com.keyforge.iiq.rest.RestConfig;
import com.keyforge.iiq.rest.ParquetRestServer;
import com.keyforge.iiq.workgroupmember.WorkgroupMemberService;
import com.keyforge.iiq.workgroupmember.WorkgroupMembership;
import com.keyforge.iiq.workgroupmember.WorkgroupMemberPersistenceService;
import com.keyforge.iiq.roleentitlement.RoleEntitlementService;
import com.keyforge.iiq.roleentitlement.RoleEntitlementGrant;
import com.keyforge.iiq.roleentitlement.RoleEntitlementRow;
import com.keyforge.iiq.roleentitlement.RoleEntitlementRowMapper;
import com.keyforge.iiq.roleentitlement.RoleEntitlementPersistenceService;
import com.keyforge.iiq.workflow.WorkflowService;
import com.keyforge.iiq.workflow.WorkflowDefinition;
import com.keyforge.iiq.workflow.WorkflowPersistenceService;
import com.keyforge.iiq.violation.ViolationService;
import com.keyforge.iiq.violation.PolicyViolation;
import com.keyforge.iiq.violation.ViolationPersistenceService;
import com.keyforge.iiq.policy.PolicyService;
import com.keyforge.iiq.policy.PolicyDefinition;
import com.keyforge.iiq.policy.PolicyPersistenceService;
import com.keyforge.iiq.accessrequest.AccessRequestService;
import com.keyforge.iiq.accessrequest.AccessRequest;
import com.keyforge.iiq.accessrequest.AccessRequestPersistenceService;
import com.keyforge.iiq.auditevent.AuditEventService;
import com.keyforge.iiq.auditevent.AuditEvent;
import com.keyforge.iiq.auditevent.AuditEventPersistenceService;
import com.keyforge.iiq.eventlink.EventLinkPersistenceService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTransaction;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTransactionService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningEventLinkPersistenceService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningTxnPersistenceService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItem;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItemService;
import com.keyforge.iiq.provisioningtransaction.ProvisioningItemPersistenceService;
import com.keyforge.iiq.taskresult.TaskResult;
import com.keyforge.iiq.taskresult.TaskResultService;
import com.keyforge.iiq.taskresult.TaskResultPersistenceService;
import com.keyforge.iiq.certification.CertificationCampaign;
import com.keyforge.iiq.certification.CertificationCampaignService;
import com.keyforge.iiq.certification.CertificationCampaignPersistenceService;
import com.keyforge.iiq.runledger.RunLedger;
import com.keyforge.nativeload.JdbcNativeApplicationSink;
import com.keyforge.nativeload.JdbcNativeIdentitySink;
import com.keyforge.nativeload.JdbcNativeLinkSink;
import com.keyforge.nativeload.JdbcNativeManagedAttributeSink;
import com.keyforge.nativeload.JdbcNativeRoleSink;
import com.keyforge.nativeload.NativeApplicationClient;
import com.keyforge.nativeload.NativeApplicationImportService;
import com.keyforge.nativeload.NativeApplicationRepository;
import com.keyforge.nativeload.NativeApplicationSink;
import com.keyforge.nativeload.JdbcNativeTaskResultSink;
import com.keyforge.nativeload.NativeTaskResultClient;
import com.keyforge.nativeload.NativeTaskResultImportService;
import com.keyforge.nativeload.NativeTaskResultRepository;
import com.keyforge.nativeload.NativeTaskResultSink;
import com.keyforge.nativeload.JdbcNativeTaskScheduleSink;
import com.keyforge.nativeload.NativeTaskScheduleClient;
import com.keyforge.nativeload.NativeTaskScheduleImportService;
import com.keyforge.nativeload.NativeTaskScheduleRepository;
import com.keyforge.nativeload.NativeTaskScheduleSink;
import com.keyforge.nativeload.JdbcNativeViolationSink;
import com.keyforge.nativeload.NativeViolationClient;
import com.keyforge.nativeload.NativeViolationImportService;
import com.keyforge.nativeload.NativeViolationRepository;
import com.keyforge.nativeload.NativeViolationSink;
import com.keyforge.nativeload.JdbcNativePolicySink;
import com.keyforge.nativeload.NativePolicyClient;
import com.keyforge.nativeload.NativePolicyImportService;
import com.keyforge.nativeload.NativePolicyRepository;
import com.keyforge.nativeload.NativePolicySink;
import com.keyforge.nativeload.JdbcNativeAuditEventSink;
import com.keyforge.nativeload.NativeAuditEventClient;
import com.keyforge.nativeload.NativeAuditEventImportService;
import com.keyforge.nativeload.NativeAuditEventRepository;
import com.keyforge.nativeload.NativeAuditEventSink;
import com.keyforge.nativeload.JdbcNativePolicyConstraintSink;
import com.keyforge.nativeload.NativePolicyConstraintClient;
import com.keyforge.nativeload.NativePolicyConstraintImportService;
import com.keyforge.nativeload.NativePolicyConstraintRepository;
import com.keyforge.nativeload.NativePolicyConstraintSink;
import com.keyforge.nativeload.JdbcNativeSyslogEventSink;
import com.keyforge.nativeload.NativeSyslogEventClient;
import com.keyforge.nativeload.NativeSyslogEventImportService;
import com.keyforge.nativeload.NativeSyslogEventRepository;
import com.keyforge.nativeload.NativeSyslogEventSink;
import com.keyforge.nativeload.NativeLinkClient;
import com.keyforge.nativeload.NativeLinkImportService;
import com.keyforge.nativeload.NativeLinkRepository;
import com.keyforge.nativeload.NativeLinkSink;
import com.keyforge.nativeload.NativeRoleClient;
import com.keyforge.nativeload.NativeRoleImportService;
import com.keyforge.nativeload.NativeRoleRepository;
import com.keyforge.nativeload.NativeRoleSink;
import com.keyforge.nativeload.JdbcNativeWorkgroupSink;
import com.keyforge.nativeload.NativeWorkgroupClient;
import com.keyforge.nativeload.NativeWorkgroupImportService;
import com.keyforge.nativeload.NativeWorkgroupRepository;
import com.keyforge.nativeload.NativeWorkgroupSink;
import com.keyforge.nativeload.JdbcNativeGroupDefinitionSink;
import com.keyforge.nativeload.NativeGroupDefinitionClient;
import com.keyforge.nativeload.NativeGroupDefinitionImportService;
import com.keyforge.nativeload.NativeGroupDefinitionRepository;
import com.keyforge.nativeload.NativeGroupDefinitionSink;
import com.keyforge.nativeload.NativeIdentityClient;
import com.keyforge.nativeload.NativeIdentityImportService;
import com.keyforge.nativeload.NativeIdentityRepository;
import com.keyforge.nativeload.NativeIdentitySink;
import com.keyforge.nativeload.NativeManagedAttributeClient;
import com.keyforge.nativeload.NativeManagedAttributeImportService;
import com.keyforge.nativeload.NativeManagedAttributeRepository;
import com.keyforge.nativeload.NativeManagedAttributeSink;
import com.keyforge.nativeload.NativeImportException;
import com.keyforge.nativeload.NativeSchemaConfig;
import com.keyforge.iiq.canonical.CanonicalViewReconciliationService;
import com.keyforge.iiq.incremental.ExtractionMode;
import com.keyforge.iiq.incremental.IncrementalConfig;
import com.keyforge.iiq.incremental.IncrementalFilter;
import com.keyforge.iiq.incremental.SourceChangeTime;
import com.keyforge.iiq.incremental.WatermarkService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Command-line entry point for the IdentityIQ migration tool.
 *
 * <p>Usage:
 * <pre>
 *     java -jar iiq-migration-tool.jar &lt;command&gt;
 * </pre>
 *
 * <p>Supported commands:
 * <ul>
 *     <li>{@code extract-users} - retrieve all Identities from IdentityIQ and print a summary</li>
 * </ul>
 */
public final class Main {

    /** How many identities to show in the verification sample. */
    private static final int SAMPLE_SIZE = 5;

    /**
     * Extraction mode for the current command, parsed once from the CLI in {@link #main}. Defaults to
     * {@link ExtractionMode#FULL} so every existing runbook keeps its live-validated behaviour;
     * {@code --incremental} opts a supported extractor into watermark-based change filtering.
     */
    private static volatile ExtractionMode extractionMode = ExtractionMode.FULL;

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
            return;
        }

        String command = args[0];
        extractionMode = ExtractionMode.fromArgs(args);

        // Parquet workstream: one independent extractor per dataset, plus an orchestration command.
        // Data-driven so the command<->dataset mapping has a single source of truth (ParquetCommands).
        if (com.keyforge.iiq.parquet.ParquetCommands.ALL.equals(command)) {
            System.exit(runAllParquet());
            return;
        }
        String parquetDataset = com.keyforge.iiq.parquet.ParquetCommands.datasetFor(command);
        if (parquetDataset != null) {
            System.exit(runOneParquet(parquetDataset));
            return;
        }
        if ("start-rest".equals(command)) {
            runStartRest();   // blocks until the process is stopped
            return;
        }

        switch (command) {
            case "extract-users" -> System.exit(runExtractUsers());
            case "extract-users-db" -> System.exit(RunLedger.run("extract-users-db", Main::runExtractUsersDb));
            case "extract-applications" -> System.exit(runExtractApplications());
            case "extract-applications-db" -> System.exit(RunLedger.run("extract-applications-db", Main::runExtractApplicationsDb));
            case "extract-applicationinstances-db" -> System.exit(RunLedger.run("extract-applicationinstances-db", Main::runExtractApplicationInstancesDb));
            case "extract-accounts-db" -> System.exit(RunLedger.run("extract-accounts-db", Main::runExtractAccountsDb));
            case "extract-entitlements-db" -> System.exit(RunLedger.run("extract-entitlements-db", Main::runExtractEntitlementsDb));
            case "extract-assignments-db" -> System.exit(RunLedger.run("extract-assignments-db", Main::runExtractAssignmentsDb));
            case "extract-catalog-db" -> System.exit(RunLedger.run("extract-catalog-db", Main::runExtractCatalogDb));
            case "extract-usergroups-db" -> System.exit(RunLedger.run("extract-usergroups-db", Main::runExtractUserGroupsDb));
            case "extract-workitems-db" -> System.exit(RunLedger.run("extract-workitems-db", Main::runExtractWorkItemsDb));
            case "extract-roles" -> System.exit(runExtractRoles());
            case "extract-roles-db" -> System.exit(RunLedger.run("extract-roles-db", Main::runExtractRolesDb));
            case "extract-role-hierarchy-db" -> System.exit(RunLedger.run("extract-role-hierarchy-db", Main::runExtractRoleHierarchyDb));
            case "extract-role-entitlements" -> System.exit(runExtractRoleEntitlements());
            case "extract-role-entitlements-db" -> System.exit(RunLedger.run("extract-role-entitlements-db", Main::runExtractRoleEntitlementsDb));
            case "extract-workflows" -> System.exit(runExtractWorkflows());
            case "extract-workflows-db" -> System.exit(RunLedger.run("extract-workflows-db", Main::runExtractWorkflowsDb));
            case "extract-policy-violations" -> System.exit(runExtractPolicyViolations());
            case "extract-policy-violations-db" -> System.exit(RunLedger.run("extract-policy-violations-db", Main::runExtractPolicyViolationsDb));
            case "extract-policies" -> System.exit(runExtractPolicies());
            case "extract-policies-db" -> System.exit(RunLedger.run("extract-policies-db", Main::runExtractPoliciesDb));
            case "extract-access-requests" -> System.exit(runExtractAccessRequests());
            case "extract-access-requests-db" -> System.exit(RunLedger.run("extract-access-requests-db", Main::runExtractAccessRequestsDb));
            case "extract-audit-events" -> System.exit(runExtractAuditEvents());
            case "extract-audit-events-db" -> System.exit(RunLedger.run("extract-audit-events-db", Main::runExtractAuditEventsDb));
            case "derive-event-links-db" -> System.exit(RunLedger.run("derive-event-links-db", Main::runDeriveEventLinksDb));
            case "derive-events-db" -> System.exit(RunLedger.run("derive-events-db", Main::runDeriveEventsDb));
            case "extract-provisioning-transactions" -> System.exit(runExtractProvisioningTransactions());
            case "extract-provisioning-transactions-db" -> System.exit(RunLedger.run("extract-provisioning-transactions-db", Main::runExtractProvisioningTransactionsDb));
            case "extract-provisioning-items" -> System.exit(runExtractProvisioningItems());
            case "extract-provisioning-items-db" -> System.exit(RunLedger.run("extract-provisioning-items-db", Main::runExtractProvisioningItemsDb));
            case "derive-provisioning-links-db" -> System.exit(RunLedger.run("derive-provisioning-links-db", Main::runDeriveProvisioningLinksDb));
            case "reconcile-canonical-views-db" -> System.exit(RunLedger.run("reconcile-canonical-views-db", Main::runReconcileCanonicalViewsDb));
            case "reconcile-referential-integrity-db" -> System.exit(RunLedger.run("reconcile-referential-integrity-db", Main::runReconcileReferentialIntegrityDb));
            case "derive-record-lineage-db" -> System.exit(RunLedger.run("derive-record-lineage-db", Main::runDeriveRecordLineageDb));
            case "reconcile-counts-db" -> System.exit(RunLedger.run("reconcile-counts-db", Main::runReconcileCountsDb));
            case "reconcile-native-workitems-db" -> System.exit(RunLedger.run("reconcile-native-workitems-db", Main::runReconcileNativeWorkItemsDb));
            case "derive-native-events-db" -> System.exit(RunLedger.run("derive-native-events-db", Main::runDeriveNativeEventsDb));
            case "extract-task-results" -> System.exit(runExtractTaskResults());
            case "extract-task-results-db" -> System.exit(RunLedger.run("extract-task-results-db", Main::runExtractTaskResultsDb));
            case "extract-certifications" -> System.exit(runExtractCertifications());
            case "extract-certifications-db" -> System.exit(RunLedger.run("extract-certifications-db", Main::runExtractCertificationsDb));
            case "extract-workgroups" -> System.exit(runExtractWorkgroups());
            case "extract-workgroups-db" -> System.exit(RunLedger.run("extract-workgroups-db", Main::runExtractWorkgroupsDb));
            case "extract-workgroup-members" -> System.exit(runExtractWorkgroupMembers());
            case "extract-workgroup-members-db" -> System.exit(RunLedger.run("extract-workgroup-members-db", Main::runExtractWorkgroupMembersDb));
            case "extract-object-owners" -> System.exit(runExtractObjectOwners());
            case "extract-object-owners-db" -> System.exit(RunLedger.run("extract-object-owners-db", Main::runExtractObjectOwnersDb));
            case "extract-account-entitlements" -> System.exit(runExtractAccountEntitlements());
            case "extract-account-entitlements-db" -> System.exit(RunLedger.run("extract-account-entitlements-db", Main::runExtractAccountEntitlementsDb));
            case "extract-identity-entitlements" -> System.exit(runExtractIdentityEntitlements());
            case "extract-identity-entitlements-db" -> System.exit(RunLedger.run("extract-identity-entitlements-db", Main::runExtractIdentityEntitlementsDb));
            case "extract-identity-roles" -> System.exit(runExtractIdentityRoles());
            case "extract-identity-roles-db" -> System.exit(RunLedger.run("extract-identity-roles-db", Main::runExtractIdentityRolesDb));
            case "extract-native-identity-db" -> System.exit(RunLedger.run("extract-native-identity-db", Main::runExtractNativeIdentityDb));
            case "extract-native-entitlement-db" -> System.exit(RunLedger.run("extract-native-entitlement-db", Main::runExtractNativeEntitlementDb));
            case "extract-native-application-db" -> System.exit(RunLedger.run("extract-native-application-db", Main::runExtractNativeApplicationDb));
            case "extract-native-account-db" -> System.exit(RunLedger.run("extract-native-account-db", Main::runExtractNativeAccountDb));
            case "extract-native-role-db" -> System.exit(RunLedger.run("extract-native-role-db", Main::runExtractNativeRoleDb));
            case "extract-native-role-relationships-db" -> System.exit(RunLedger.run("extract-native-role-relationships-db", Main::runExtractNativeRoleRelationshipsDb));
            case "extract-native-identity-role-db" -> System.exit(RunLedger.run("extract-native-identity-role-db", Main::runExtractNativeIdentityRoleDb));
            case "extract-native-task-result-db" -> System.exit(RunLedger.run("extract-native-task-result-db", Main::runExtractNativeTaskResultDb));
            case "extract-native-task-schedule-db" -> System.exit(RunLedger.run("extract-native-task-schedule-db", Main::runExtractNativeTaskScheduleDb));
            case "extract-native-violations-db" -> System.exit(RunLedger.run("extract-native-violations-db", Main::runExtractNativeViolationsDb));
            case "extract-native-policies-db" -> System.exit(RunLedger.run("extract-native-policies-db", Main::runExtractNativePoliciesDb));
            case "extract-native-audit-events-db" -> System.exit(RunLedger.run("extract-native-audit-events-db", Main::runExtractNativeAuditEventsDb));
            case "extract-native-policy-constraints-db" -> System.exit(RunLedger.run("extract-native-policy-constraints-db", Main::runExtractNativePolicyConstraintsDb));
            case "extract-native-syslog-events-db" -> System.exit(RunLedger.run("extract-native-syslog-events-db", Main::runExtractNativeSyslogEventsDb));
            case "extract-native-parquet" -> System.exit(RunLedger.run("extract-native-parquet", Main::runExtractNativeParquet));
            case "extract-native-workgroup-db" -> System.exit(RunLedger.run("extract-native-workgroup-db", Main::runExtractNativeWorkgroupDb));
            case "extract-native-group-definition-db" -> System.exit(RunLedger.run("extract-native-group-definition-db", Main::runExtractNativeGroupDefinitionDb));
            case "extract-native-workitem-archive-db" -> System.exit(RunLedger.run("extract-native-workitem-archive-db", Main::runExtractNativeWorkItemArchiveDb));
            case "extract-native-workflows-db" -> System.exit(RunLedger.run("extract-native-workflows-db", Main::runExtractNativeWorkflowsDb));
            case "extract-native-workgroup-memberships-db" -> System.exit(RunLedger.run("extract-native-workgroup-memberships-db", Main::runExtractNativeWorkgroupMembershipsDb));
            case "extract-native-identity-entitlement-db" -> System.exit(RunLedger.run("extract-native-identity-entitlement-db", Main::runExtractNativeIdentityEntitlementDb));
            case "extract-native-account-entitlement-db" -> System.exit(RunLedger.run("extract-native-account-entitlement-db", Main::runExtractNativeAccountEntitlementDb));
            case "extract-native-certification-db" -> System.exit(RunLedger.run("extract-native-certification-db", Main::runExtractNativeCertificationDb));
            case "extract-native-certification-entity-db" -> System.exit(RunLedger.run("extract-native-certification-entity-db", Main::runExtractNativeCertificationEntityDb));
            case "extract-native-certification-item-db" -> System.exit(RunLedger.run("extract-native-certification-item-db", Main::runExtractNativeCertificationItemDb));
            case "extract-native-certification-archive-db" -> System.exit(RunLedger.run("extract-native-certification-archive-db", Main::runExtractNativeCertificationArchiveDb));
            case "extract-native-provisioning-txn-db" -> System.exit(RunLedger.run("extract-native-provisioning-txn-db", Main::runExtractNativeProvisioningTxnDb));
            case "extract-native-identity-request-db" -> System.exit(RunLedger.run("extract-native-identity-request-db", Main::runExtractNativeIdentityRequestDb));
            case "extract-native-workitem-db" -> System.exit(RunLedger.run("extract-native-workitem-db", Main::runExtractNativeWorkItemDb));
            case "extract-entitlements" -> System.exit(runExtractEntitlements());
            case "extract-accounts" -> System.exit(runExtractAccounts());
            case "extract-assignments" -> System.exit(runExtractAssignments());
            case "-h", "--help", "help" -> {
                printUsage();
                System.exit(0);
            }
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(2);
            }
        }
    }

    private static int runExtractUsers() {
        try {
            // 1. Configuration (fails clearly if anything is missing).
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            // 2. HTTP client + 3. service.
            IiqApiClient client = new IiqApiClient(config);
            IdentityService identityService = new IdentityService(client);

            // 4. Retrieve all identities (paginated).
            List<Identity> identities = identityService.getAllIdentities();

            // 5. Summary + lightweight verification output.
            printSummary(identities);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractCatalogDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Reuse the existing, verified Entitlement extraction (no new API).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Entitlement> entitlements =
                    new EntitlementService(client).getAllEntitlements();

            long requestableCount = entitlements.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getRequestable()))
                    .count();

            System.out.println("Extracted " + entitlements.size()
                    + " entitlements from IdentityIQ");
            System.out.println("Requestable entitlement candidates: " + requestableCount);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                CatalogPersistenceService svc = new CatalogPersistenceService(pgConfig.getSchema());
                CatalogPersistenceService.Result result = svc.persist(conn, entitlements);
                RunLedger.record("catalog", entitlements.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                printCatalogDbSummary(result, svc.targetTable());
                // CSS deletion detection: rebuild the keep-set from the SAME requestable derivation that
                // persistence uses. catalogid = deterministicCatalogId(name, appinstanceid) is independent
                // of the existing-id sets, so empty sets reproduce exactly the persisted catalogids. The
                // requestable filter makes this correctly delete rows whose entitlement was removed OR is
                // no longer requestable. A full (single) getAllEntitlements pull -> no partial-fetch risk.
                List<Entitlement> requestableForSweep = entitlements.stream()
                        .filter(e -> Boolean.TRUE.equals(e.getRequestable())).toList();
                List<com.keyforge.iiq.catalog.CatalogRow> catalogKeepRows =
                        com.keyforge.iiq.catalog.CatalogRowMapper.deriveCatalogRows(
                                requestableForSweep, java.util.Set.of(), java.util.Set.of());
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "catalog", "catalogid", catalogKeepRows,
                        com.keyforge.iiq.catalog.CatalogRow::catalogid);
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printCatalogDbSummary(CatalogPersistenceService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Persisted entitlement catalog to " + targetTable);
        System.out.println("  derived catalog identities:         " + result.getDerived());
        System.out.println("  distinct catalog ids:               " + result.getDistinctCatalogIds());
        System.out.println("  inserted:                           " + result.getInserted());
        System.out.println("  updated:                            " + result.getUpdated());
        System.out.println("  failed:                             " + result.getFailed());
        System.out.println("  unresolved entitlement references:  " + result.getUnresolvedEntitlements());
        System.out.println("  unresolved app-instance references: " + result.getUnresolvedInstances());
        System.out.println("  persisted:                          " + result.getPersisted());

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractUserGroupsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Classic IIQ Group Configuration endpoints need a logged-in web session, not
            // Basic auth: IiqSessionClient (an IiqApiClient) establishes it from IIQ_USERNAME/
            // IIQ_PASSWORD and replays the session cookie on every call. Request logging is on
            // by default here to diagnose the live requests; set IIQ_DEBUG_HTTP=false to silence.
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqApiClient client = new IiqSessionClient(iiqConfig, debugHttp);
            UserGroupService.Result extraction = new UserGroupService(client).extractAll();

            List<UserGroup> extracted = extraction.getUserGroups();
            System.out.println("User group sources:");
            System.out.println("  - Workgroups: " + countByType(extracted, UserGroupService.TYPE_WORKGROUP));
            System.out.println("  - Populations: " + countByType(extracted, UserGroupService.TYPE_POPULATION));
            System.out.println("  - Groups: " + countByType(extracted, UserGroupService.TYPE_GROUP));
            if (extraction.anyEndpointFailed()) {
                System.out.println("  (one or more sources failed:)");
                for (String note : extraction.getSourceStatus()) {
                    System.out.println("    - " + note);
                }
            }
            System.out.println();
            System.out.println("Extracted " + extracted.size() + " user group records");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                UserGroupPersistenceService svc = new UserGroupPersistenceService(pgConfig.getSchema());
                UserGroupPersistenceService.Result result = svc.persist(conn, extraction.getUserGroups());
                RunLedger.record("usergroup", extraction.getUserGroups().size(), result.getInserted(), result.getUpdated(), result.getFailed());
                printUserGroupDbSummary(result, svc.targetTable());
                if (result.getFailed() > 0) {
                    return 6;
                }
                // A source endpoint that errored is surfaced, not hidden as zero.
                return extraction.anyEndpointFailed() ? 4 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUserGroupDbSummary(UserGroupPersistenceService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Persisted user groups to " + targetTable + ":");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated: " + result.getUpdated());
        System.out.println("  failed: " + result.getFailed());

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /** Counts extracted user groups of a given source category. */
    private static long countByType(List<UserGroup> groups, String type) {
        return groups.stream().filter(g -> type.equals(g.getType())).count();
    }

    private static int runExtractWorkItemsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // The Work Items page uses a POST to ui/rest/workItems/ that needs a logged-in
            // web session + CSRF token, not Basic auth — IiqSessionClient provides both.
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqApiClient client = new IiqSessionClient(iiqConfig, debugHttp);
            List<WorkItem> workItems = new WorkItemService(client).getAllWorkItems();
            System.out.println("Extracted " + workItems.size() + " work item(s) from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                WorkItemPersistenceService svc = new WorkItemPersistenceService(pgConfig.getSchema());
                WorkItemPersistenceService.Result result = svc.persist(conn, workItems);
                RunLedger.record("workitem", workItems.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Persisted work items to " + svc.targetTable() + ":");
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated: " + result.getUpdated());
                System.out.println("  failed: " + result.getFailed());
                int shown = Math.min(result.getFailures().size(), 10);
                for (int i = 0; i < shown; i++) {
                    System.out.println("    - " + result.getFailures().get(i));
                }
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractAssignmentsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Reuse the existing, verified extraction + derivation (unchanged).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);
            System.out.println("Derived " + assignments.size() + " account-entitlement assignments"
                    + " (from " + accounts.size() + " accounts, " + entitlements.size() + " entitlements)");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                EntitlementAssignmentPersistenceService svc =
                        new EntitlementAssignmentPersistenceService(pgConfig.getSchema());
                EntitlementAssignmentPersistenceService.Result result = svc.persist(conn, assignments);
                RunLedger.record("entitlementassignment", assignments.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                printAssignmentDbSummary(result, svc.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printAssignmentDbSummary(EntitlementAssignmentPersistenceService.Result result,
                                                 String targetTable) {
        System.out.println();
        System.out.println("Persisted account-entitlement assignments to " + targetTable);
        System.out.println("  derived:                 " + result.getDerived());
        System.out.println("  distinct assignment ids: " + result.getDistinctAssignmentIds());
        if (result.getDuplicateAssignmentIds() > 0) {
            System.out.println("  duplicate logical keys:  " + result.getDuplicateAssignmentIds());
        }
        System.out.println("  inserted:                " + result.getInserted());
        System.out.println("  updated:                 " + result.getUpdated());
        System.out.println("  failed:                  " + result.getFailed());
        System.out.println("  unresolved accounts:     " + result.getUnresolvedAccounts());
        System.out.println("  unresolved entitlements: " + result.getUnresolvedEntitlements());
        System.out.println("  persisted:               " + result.getPersisted());

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractEntitlementsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Extract entitlement definitions with the existing service (paginated).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            System.out.println("Extracted " + entitlements.size() + " entitlements from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<Entitlement> plan = planExtraction(
                        conn, schema, "kf_entitlement", "meta.lastModified", entitlements, Main::entitlementChangeTime);
                EntitlementPersistenceService svc = new EntitlementPersistenceService(schema);
                EntitlementPersistenceService.Result result = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_entitlement", plan.kept(), result.getInserted(), result.getUpdated(), result.getFailed());
                advanceWatermark(conn, schema, "kf_entitlement", "meta.lastModified", plan, result.getFailed());
                sweepEntityDeletions(conn, schema, "kf_entitlement", "entitlementid", entitlements, Entitlement::getId);
                printEntitlementDbSummary(plan.kept(), result, svc.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printEntitlementDbSummary(int extracted, EntitlementPersistenceService.Result result,
                                                  String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " entitlements to " + targetTable + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        System.out.println("  application not resolved (instanceid NULL): " + result.getUnresolvedInstances().size());

        printLines(result.getUnresolvedInstances());
        printLines(result.getFailures());
    }

    private static int runExtractAccountsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Extract accounts with the existing, verified service (unchanged).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            System.out.println("Extracted " + accounts.size() + " accounts from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<Account> plan = planExtraction(
                        conn, schema, "kf_account", "meta.lastModified", accounts, Main::accountChangeTime);
                AccountPersistenceService svc = new AccountPersistenceService(schema);
                AccountPersistenceService.Result result = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_account", plan.kept(), result.getInserted(), result.getUpdated(), result.getFailed());
                advanceWatermark(conn, schema, "kf_account", "meta.lastModified", plan, result.getFailed());
                sweepEntityDeletions(conn, schema, "kf_account", "accountid", accounts, Account::getId);
                printAccountDbSummary(plan.kept(), result, svc.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printAccountDbSummary(int extracted, AccountPersistenceService.Result result,
                                              String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " accounts to " + targetTable + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        System.out.println("  identity not resolved (userid NULL): " + result.getUnresolvedUsers().size());
        System.out.println("  application not resolved (instanceid NULL): " + result.getUnresolvedInstances().size());

        printLines(result.getUnresolvedUsers());
        printLines(result.getUnresolvedInstances());
        printLines(result.getFailures());
    }

    private static int runExtractApplicationInstancesDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Extract applications with the existing, verified service (unchanged).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Application> applications = new ApplicationService(client).getAllApplications();
            System.out.println("Extracted " + applications.size() + " applications from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ApplicationInstancePersistenceService svc =
                        new ApplicationInstancePersistenceService(pgConfig.getSchema());
                ApplicationInstancePersistenceService.Result result = svc.persist(conn, applications);
                RunLedger.record("applicationinstance", applications.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                printApplicationInstanceDbSummary(applications.size(), result, svc.targetTable(),
                        pgConfig.getSchema());
                // CSS deletion detection: applicationinstance is 1:1 with the FULL applications pull;
                // instanceid = canonicalUuid(application.getId()) (identical to the mapper), so the keep-set
                // reproduces the persisted PKs exactly. Built from the live pull (not kf_application), so a
                // soft-deleted application row cannot cause a false instance deletion; empty-source guarded.
                sweepEntityDeletions(conn, pgConfig.getSchema(), "applicationinstance", "instanceid",
                        applications, Application::getId);
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printApplicationInstanceDbSummary(int extracted,
                                                          ApplicationInstancePersistenceService.Result result,
                                                          String targetTable, String schema) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " application instances to " + targetTable
                + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        System.out.println("  owner not resolved (ownerid NULL): " + result.getUnresolvedOwners().size());
        System.out.println("  application not in " + schema + ".kf_application (applicationid NULL): "
                + result.getApplicationsNotFound().size());

        printLines(result.getApplicationsNotFound());
        printLines(result.getUnresolvedOwners());
        printLines(result.getFailures());
    }

    private static void printLines(List<String> lines) {
        int shown = Math.min(lines.size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + lines.get(i));
        }
        if (lines.size() > shown) {
            System.out.println("    ... and " + (lines.size() - shown) + " more");
        }
    }

    // ---- Incremental extraction (watermark-based, client-side change filtering) ----
    //
    // No verified IIQ read interface exposes a server-side "modified since" filter, so the same pages
    // are always read; incremental mode only narrows what we PERSIST. A record is kept when its
    // authoritative SOURCE-side change time is at/after the EFFECTIVE watermark = stored watermark
    // minus the configured overlap window (Connector Design 4.1; INCREMENTAL_OVERLAP_MINUTES). The
    // overlap re-admits records committed slightly before the watermark (clock skew / late commits);
    // idempotent upserts absorb the harmless re-processing. The persisted watermark advances to the
    // max source change time only after a fully successful run (WatermarkService.shouldAdvance) and
    // never regresses below the stored value. Wired only into extractors whose interface returns a
    // real UTC change timestamp; other sources stay full-only by design.

    /** Reads the entity's watermark, applies the filter (with overlap) for the current mode, logs the plan. */
    private static <T> IncrementalFilter.Result<T> planExtraction(
            Connection conn, String schema, String entity, String watermarkField,
            List<T> extracted, Function<T, Instant> changeTime) throws SQLException {
        Duration overlap = IncrementalConfig.overlapWindow();
        Instant prior = new WatermarkService(schema).readWatermark(conn, entity);
        IncrementalFilter.Result<T> plan =
                IncrementalFilter.apply(extracted, changeTime, prior, extractionMode, overlap);
        if (extractionMode.isIncremental()) {
            Instant boundary = IncrementalFilter.effectiveWatermark(prior, extractionMode, overlap);
            System.out.println("Incremental mode [" + entity + "]: watermark(" + watermarkField + ")="
                    + (prior == null ? "none (first run -> full load)" : prior)
                    + "  overlap=" + overlap.toMinutes() + "m"
                    + "  effective-boundary=" + (boundary == null ? "n/a" : boundary)
                    + "  extracted=" + plan.total() + "  changed=" + plan.kept()
                    + "  unchanged-skipped=" + plan.skipped());
        } else {
            System.out.println("Full mode [" + entity + "]: processing all " + plan.total()
                    + " extracted records (watermark not applied)");
        }
        return plan;
    }

    /** Advances the watermark iff the run had zero row-level failures; logs the decision. */
    private static void advanceWatermark(Connection conn, String schema, String entity,
            String watermarkField, IncrementalFilter.Result<?> plan, int failed) throws SQLException {
        boolean advanced = new WatermarkService(schema).advanceIfSuccessful(
                conn, entity, watermarkField, plan.newWatermark(), failed, RunLedger.currentRunId());
        if (advanced) {
            System.out.println("Watermark advanced [" + entity + "] -> " + plan.newWatermark());
        } else if (failed > 0) {
            System.out.println("Watermark NOT advanced [" + entity + "] (row-level failures present; "
                    + "next incremental run will re-scan from the unchanged watermark)");
        }
    }

    /**
     * CSS deletion detection / soft-delete for a current-state entity table. Builds the authoritative
     * keep-set from the FULL source pull (always fetched, even in incremental mode) using the same
     * canonicalization the repositories store ({@link com.keyforge.iiq.parquet.ParquetIds#canonicalUuid}
     * == each RowMapper's {@code toCanonicalUuid}), then marks rows no longer in the source deleted and
     * revives reappeared ones. An object with an unparseable id is skipped (it was never persisted), so
     * the keep-set never wrongly omits a stored row. Reuses the proven {@link SoftDeleteSweeper}.
     */
    private static <T> void sweepEntityDeletions(Connection conn, String schema, String table,
            String pkColumn, List<T> fullSource, java.util.function.Function<T, String> rawIdFn) {
        List<String> ids = new java.util.ArrayList<>(fullSource.size());
        for (T t : fullSource) {
            String canon = com.keyforge.iiq.parquet.ParquetIds.canonicalUuid(rawIdFn.apply(t));
            if (canon != null) {
                ids.add(canon);
            }
        }
        try {
            com.keyforge.iiq.deletion.SoftDeleteSweeper.SweepResult sw =
                    new com.keyforge.iiq.deletion.SoftDeleteSweeper(schema).sweep(conn, table, pkColumn, ids);
            if (sw.skipped()) {
                System.out.println("  soft-delete sweep [" + table + "]: SKIPPED — " + sw.note());
            } else {
                System.out.println("  soft-delete sweep [" + table + "]: current-source=" + sw.currentIdCount()
                        + " marked deleted=" + sw.marked() + " revived=" + sw.revived());
            }
        } catch (SQLException e) {
            System.out.println("  soft-delete sweep [" + table + "]: ERROR — " + e.getMessage());
        }
    }

    /**
     * Variant of {@link #sweepEntityDeletions} for tables whose PK is a <b>precomputed deterministic
     * id</b> (not a canonicalized source id) — e.g. {@code kf_workgroup_member.id =
     * deterministicId(workgroup_id, identity_id)}. The caller supplies a {@code pkFn} that reproduces
     * the exact stored PK (using the same RowMapper), so the keep-set matches what was persisted. A
     * source record that cannot form a valid PK was never persisted, so excluding it is safe. Reuses
     * the same {@link SoftDeleteSweeper} (empty-source guard, revive, idempotency, no hard delete).
     */
    private static <T> void sweepDeletionsByPk(Connection conn, String schema, String table,
            String pkColumn, List<T> fullSource, java.util.function.Function<T, String> pkFn) {
        List<String> ids = new java.util.ArrayList<>(fullSource.size());
        for (T t : fullSource) {
            try {
                String pk = pkFn.apply(t);
                if (pk != null && !pk.isBlank()) {
                    ids.add(pk);
                }
            } catch (RuntimeException unmappable) {
                // record that cannot form a valid PK was never persisted -> safe to exclude from keep-set
            }
        }
        try {
            com.keyforge.iiq.deletion.SoftDeleteSweeper.SweepResult sw =
                    new com.keyforge.iiq.deletion.SoftDeleteSweeper(schema).sweep(conn, table, pkColumn, ids);
            if (sw.skipped()) {
                System.out.println("  soft-delete sweep [" + table + "]: SKIPPED — " + sw.note());
            } else {
                System.out.println("  soft-delete sweep [" + table + "]: current-source=" + sw.currentIdCount()
                        + " marked deleted=" + sw.marked() + " revived=" + sw.revived());
            }
        } catch (SQLException e) {
            System.out.println("  soft-delete sweep [" + table + "]: ERROR — " + e.getMessage());
        }
    }

    // Per-entity source-change-time extractors (authoritative SOURCE fields only; never processing time).

    private static Instant taskResultChangeTime(TaskResult t) {
        return SourceChangeTime.max(SourceChangeTime.parseIso(t.completed()),
                SourceChangeTime.parseIso(t.launched()));
    }

    private static Instant workflowChangeTime(WorkflowDefinition w) {
        return SourceChangeTime.max(SourceChangeTime.parseIso(w.lastModified()),
                SourceChangeTime.parseIso(w.created()));
    }

    private static Instant accountChangeTime(Account a) {
        Account.Meta m = a.getMeta();
        return m == null ? null : SourceChangeTime.max(
                SourceChangeTime.parseIso(m.getLastModified()), SourceChangeTime.parseIso(m.getCreated()));
    }

    private static Instant applicationChangeTime(Application a) {
        Application.Meta m = a.getMeta();
        return m == null ? null : SourceChangeTime.max(
                SourceChangeTime.parseIso(m.getLastModified()), SourceChangeTime.parseIso(m.getCreated()));
    }

    private static Instant entitlementChangeTime(Entitlement e) {
        Entitlement.Meta m = e.getMeta();
        return m == null ? null : SourceChangeTime.max(
                SourceChangeTime.parseIso(m.getLastModified()), SourceChangeTime.parseIso(m.getCreated()));
    }

    private static Instant roleChangeTime(Role r) {
        Role.Meta m = r.getMeta();
        return m == null ? null : SourceChangeTime.max(
                SourceChangeTime.parseIso(m.getLastModified()), SourceChangeTime.parseIso(m.getCreated()));
    }

    private static Instant identityChangeTime(Identity i) {
        com.fasterxml.jackson.databind.JsonNode ext = i.getExtendedAttributes();
        if (ext == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode meta = ext.path("meta");
        return SourceChangeTime.max(
                SourceChangeTime.parseIso(nodeText(meta.path("lastModified"))),
                SourceChangeTime.parseIso(nodeText(meta.path("created"))));
    }

    private static String nodeText(com.fasterxml.jackson.databind.JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private static int runExtractApplicationsDb() {
        try {
            // 1. Load IIQ + PostgreSQL configuration (both fail clearly if incomplete).
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // 2-3. Extract all applications from IdentityIQ (unchanged, verified service).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Application> applications = new ApplicationService(client).getAllApplications();
            System.out.println("Extracted " + applications.size() + " applications from IdentityIQ");

            // 4-6. Map and persist into the configured schema's application table.
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<Application> plan = planExtraction(
                        conn, schema, "kf_application", "meta.lastModified", applications, Main::applicationChangeTime);
                ApplicationPersistenceService svc = new ApplicationPersistenceService(schema);
                ApplicationPersistenceService.Result result = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_application", plan.kept(), result.getInserted(), result.getUpdated(), result.getFailed());
                advanceWatermark(conn, schema, "kf_application", "meta.lastModified", plan, result.getFailed());
                sweepEntityDeletions(conn, schema, "kf_application", "applicationid", applications, Application::getId);
                printApplicationDbSummary(plan.kept(), result, svc.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printApplicationDbSummary(int extracted, ApplicationPersistenceService.Result result,
                                                  String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " applications to " + targetTable + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) Identity extraction: pull the KeyForge IIQ plugin's REST endpoint over the
     * existing authenticated session and load the native-rich rows into the separate
     * {@code iiq_native.kf_identity} table. IIQ-side is strictly read-only (the plugin only reads);
     * the only writes are to our PostgreSQL. Idempotent — re-running updates rows, never duplicates.
     */
    private static int runExtractNativeIdentityDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeIdentityClient.IDENTITIES_PATH + "), page size " + pageSize);

            NativeIdentityClient client = new NativeIdentityClient(new IiqSessionClient(iiqConfig));
            NativeIdentityRepository repository = new NativeIdentityRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                // Full current-state extraction: pull → upsert → deletion sweep (guarded, full-scan only).
                NativeIdentitySink sink = new JdbcNativeIdentitySink(conn, repository, nativeSchema);
                NativeIdentityImportService svc =
                        new NativeIdentityImportService(client, sink, pageSize, true);
                NativeIdentityImportService.Result result = svc.importAll();
                RunLedger.record("kf_identity", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeIdentityDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int nativePageSize() {
        String v = System.getenv("IIQ_NATIVE_PAGE_SIZE");
        if (v == null || v.isBlank()) {
            v = System.getProperty("IIQ_NATIVE_PAGE_SIZE");
        }
        if (v == null || v.isBlank()) {
            return 100;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : 100;
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static void printNativeIdentityDbSummary(NativeIdentityImportService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native identities to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) ManagedAttribute/entitlement extraction: pull the plugin's REST endpoint over
     * the existing authenticated session and load native-rich rows into {@code iiq_native.kf_entitlement}.
     * IIQ-side read-only; the only writes are to our PostgreSQL. Idempotent; full-scan deletion sweep.
     */
    private static int runExtractNativeEntitlementDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeManagedAttributeClient.ENTITLEMENTS_PATH + "), page size " + pageSize);

            NativeManagedAttributeClient client = new NativeManagedAttributeClient(new IiqSessionClient(iiqConfig));
            NativeManagedAttributeRepository repository = new NativeManagedAttributeRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeManagedAttributeSink sink = new JdbcNativeManagedAttributeSink(conn, repository, nativeSchema);
                NativeManagedAttributeImportService svc =
                        new NativeManagedAttributeImportService(client, sink, pageSize, true);
                NativeManagedAttributeImportService.Result result = svc.importAll();
                RunLedger.record("kf_entitlement", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeEntitlementDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeEntitlementDbSummary(NativeManagedAttributeImportService.Result result,
                                                        String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native entitlements to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) Application extraction: pull the plugin's REST endpoint over the existing
     * authenticated session and load native-rich rows into {@code iiq_native.kf_application}. IIQ-side
     * read-only; the only writes are to our PostgreSQL. Idempotent; full-scan deletion sweep.
     */
    private static int runExtractNativeParquet() {
        try {
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            ParquetConfig pq = ParquetConfig.load();
            System.out.println("PostgreSQL (source): " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Parquet output dir: " + pq.outputDir().toAbsolutePath());
            System.out.println("Native-source Parquet: 10 approved datasets, current-state, content-hash deduped "
                    + "(extraction timestamp EXCLUDED from the hash; one stable file per dataset — no versions)");

            String runId = RunLedger.currentRunId();
            if (runId == null) {
                runId = java.util.UUID.randomUUID().toString();
            }
            int totalRows = 0, totalNew = 0, totalChanged = 0, totalUnchanged = 0, failed = 0;
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                java.util.List<NativeParquetService.Result> results =
                        new NativeParquetService(nativeSchema).run(conn, pq.outputDir(), runId);
                System.out.println();
                System.out.printf("%-24s %6s %6s %8s %10s%n", "dataset", "rows", "new", "changed", "unchanged");
                for (NativeParquetService.Result r : results) {
                    if (r.error() != null) {
                        failed++;
                        System.out.printf("%-24s   ERROR: %s%n", r.dataset(), r.error());
                    } else {
                        totalRows += r.total(); totalNew += r.inserted();
                        totalChanged += r.changed(); totalUnchanged += r.unchanged();
                        System.out.printf("%-24s %6d %6d %8d %10d%n",
                                r.dataset(), r.total(), r.inserted(), r.changed(), r.unchanged());
                    }
                }
                RunLedger.record("native_parquet", totalRows, totalNew, totalChanged, failed);
                System.out.println();
                System.out.println("TOTAL rows=" + totalRows + " new=" + totalNew + " changed=" + totalChanged
                        + " unchanged=" + totalUnchanged + " datasets-failed=" + failed);
                return failed > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (SQLException e) { System.err.println("PostgreSQL/Parquet error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeSyslogEventsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeSyslogEventClient.SYSLOG_EVENTS_PATH + "), page size " + pageSize
                    + " [APPEND-ONLY operational log]");

            NativeSyslogEventClient client = new NativeSyslogEventClient(new IiqSessionClient(iiqConfig));
            NativeSyslogEventRepository repository = new NativeSyslogEventRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeSyslogEventSink sink = new JdbcNativeSyslogEventSink(conn, repository);
                NativeSyslogEventImportService.Result result =
                        new NativeSyslogEventImportService(client, sink, pageSize).importAll();
                RunLedger.record("kf_syslog_event", result.getExtracted(),
                        result.getInserted(), 0, result.getFailed());
                System.out.println();
                System.out.println("Source SyslogEvent count (IIQ): " + result.getSourceCount());
                System.out.println("Successfully appended " + result.getInserted() + " new native syslog events to "
                        + repository.targetTable() + " (of " + result.getExtracted() + " extracted)");
                System.out.println("  inserted (new):        " + result.getInserted());
                System.out.println("  skipped (already present): " + result.getSkipped());
                System.out.println("  failed:                " + result.getFailed());
                System.out.println("  append-only: no deletion sweep (immutable operational log)");
                int shown = Math.min(result.getFailures().size(), 10);
                for (int i = 0; i < shown; i++) {
                    System.out.println("    - " + result.getFailures().get(i));
                }
                return result.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4; }
        catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4; }
        catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativePolicyConstraintsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativePolicyConstraintClient.POLICY_CONSTRAINTS_PATH + "), page size " + pageSize
                    + " [Policy.getSODConstraints/getGenericConstraints/getActivityConstraints]");

            NativePolicyConstraintClient client = new NativePolicyConstraintClient(new IiqSessionClient(iiqConfig));
            NativePolicyConstraintRepository repository = new NativePolicyConstraintRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativePolicyConstraintSink sink = new JdbcNativePolicyConstraintSink(conn, repository, nativeSchema);
                NativePolicyConstraintImportService.Result result =
                        new NativePolicyConstraintImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_policy_constraint", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Policies scanned: " + result.getScannedPolicies() + " of " + result.getSourcePolicyCount());
                System.out.println("Successfully persisted " + result.getPersisted() + " native policy constraints to "
                        + repository.targetTable() + " (of " + result.getExtracted() + " extracted)");
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated:  " + result.getUpdated());
                System.out.println("  failed:   " + result.getFailed());
                if (result.isSweepRan()) {
                    System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
                }
                int shown = Math.min(result.getFailures().size(), 10);
                for (int i = 0; i < shown; i++) {
                    System.out.println("    - " + result.getFailures().get(i));
                }
                return result.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4; }
        catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4; }
        catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeAuditEventsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeAuditEventClient.AUDIT_EVENTS_PATH + "), page size " + pageSize
                    + " [APPEND-ONLY historical audit log]");

            NativeAuditEventClient client = new NativeAuditEventClient(new IiqSessionClient(iiqConfig));
            NativeAuditEventRepository repository = new NativeAuditEventRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeAuditEventSink sink = new JdbcNativeAuditEventSink(conn, repository);
                NativeAuditEventImportService.Result result =
                        new NativeAuditEventImportService(client, sink, pageSize).importAll();
                RunLedger.record("kf_audit_event", result.getExtracted(),
                        result.getInserted(), 0, result.getFailed());
                System.out.println();
                System.out.println("Source AuditEvent count (IIQ): " + result.getSourceCount());
                System.out.println("Successfully appended " + result.getInserted() + " new native audit events to "
                        + repository.targetTable() + " (of " + result.getExtracted() + " extracted)");
                System.out.println("  inserted (new):        " + result.getInserted());
                System.out.println("  skipped (already present): " + result.getSkipped());
                System.out.println("  failed:                " + result.getFailed());
                System.out.println("  append-only: no deletion sweep (immutable historical evidence)");
                int shown = Math.min(result.getFailures().size(), 10);
                for (int i = 0; i < shown; i++) {
                    System.out.println("    - " + result.getFailures().get(i));
                }
                return result.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4; }
        catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4; }
        catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeViolationsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeViolationClient.VIOLATIONS_PATH + "), page size " + pageSize);

            NativeViolationClient client = new NativeViolationClient(new IiqSessionClient(iiqConfig));
            NativeViolationRepository repository = new NativeViolationRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeViolationSink sink = new JdbcNativeViolationSink(conn, repository, nativeSchema);
                NativeViolationImportService.Result result =
                        new NativeViolationImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_violation", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeCurrentStateSummary("policy violations", result.getPersisted(), result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed(), result.isSweepRan(),
                        result.isSweepSkipped(), result.getMarkedDeleted(), result.getRevived(),
                        result.getFailures(), repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4; }
        catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4; }
        catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativePoliciesDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativePolicyClient.POLICIES_PATH + "), page size " + pageSize);

            NativePolicyClient client = new NativePolicyClient(new IiqSessionClient(iiqConfig));
            NativePolicyRepository repository = new NativePolicyRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativePolicySink sink = new JdbcNativePolicySink(conn, repository, nativeSchema);
                NativePolicyImportService.Result result =
                        new NativePolicyImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_policy", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeCurrentStateSummary("policies", result.getPersisted(), result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed(), result.isSweepRan(),
                        result.isSweepSkipped(), result.getMarkedDeleted(), result.getRevived(),
                        result.getFailures(), repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3; }
        catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4; }
        catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4; }
        catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5; }
        catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static void printNativeCurrentStateSummary(String label, int persisted, int extracted, int inserted,
            int updated, int failed, boolean sweepRan, boolean sweepSkipped, int marked, int revived,
            List<String> failures, String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + persisted + " native " + label + " to " + targetTable
                + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + inserted);
        System.out.println("  updated:  " + updated);
        System.out.println("  failed:   " + failed);
        if (sweepRan) {
            System.out.println("  deletion sweep: " + (sweepSkipped
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + marked + " deleted, revived " + revived)));
        }
        int shown = Math.min(failures.size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + failures.get(i));
        }
        if (failures.size() > shown) {
            System.out.println("    ... and " + (failures.size() - shown) + " more");
        }
    }

    private static int runExtractNativeTaskResultDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeTaskResultClient.TASK_RESULTS_PATH + "), page size " + pageSize);

            NativeTaskResultClient client = new NativeTaskResultClient(new IiqSessionClient(iiqConfig));
            NativeTaskResultRepository repository = new NativeTaskResultRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeTaskResultSink sink = new JdbcNativeTaskResultSink(conn, repository, nativeSchema);
                NativeTaskResultImportService svc =
                        new NativeTaskResultImportService(client, sink, pageSize, true);
                NativeTaskResultImportService.Result result = svc.importAll();
                RunLedger.record("kf_task_result", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeTaskResultDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeTaskResultDbSummary(NativeTaskResultImportService.Result result,
                                                       String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native task results to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }
        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractNativeTaskScheduleDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeTaskScheduleClient.TASK_SCHEDULES_PATH + "), page size " + pageSize);

            NativeTaskScheduleClient client = new NativeTaskScheduleClient(new IiqSessionClient(iiqConfig));
            NativeTaskScheduleRepository repository = new NativeTaskScheduleRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeTaskScheduleSink sink = new JdbcNativeTaskScheduleSink(conn, repository, nativeSchema);
                NativeTaskScheduleImportService svc =
                        new NativeTaskScheduleImportService(client, sink, pageSize, true);
                NativeTaskScheduleImportService.Result result = svc.importAll();
                RunLedger.record("kf_task_schedule", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeTaskScheduleDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeTaskScheduleDbSummary(NativeTaskScheduleImportService.Result result,
                                                         String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native task schedules to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }
        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractNativeApplicationDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeApplicationClient.APPLICATIONS_PATH + "), page size " + pageSize);

            NativeApplicationClient client = new NativeApplicationClient(new IiqSessionClient(iiqConfig));
            NativeApplicationRepository repository = new NativeApplicationRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeApplicationSink sink = new JdbcNativeApplicationSink(conn, repository, nativeSchema);
                NativeApplicationImportService svc =
                        new NativeApplicationImportService(client, sink, pageSize, true);
                NativeApplicationImportService.Result result = svc.importAll();
                RunLedger.record("kf_application", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeApplicationDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeApplicationDbSummary(NativeApplicationImportService.Result result,
                                                        String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native applications to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) Link/account extraction: pull the plugin's REST endpoint over the existing
     * authenticated session and load native-rich rows into {@code iiq_native.kf_account}. IIQ-side
     * read-only; the only writes are to our PostgreSQL. Idempotent; full-scan deletion sweep.
     */
    private static int runExtractNativeAccountDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeLinkClient.ACCOUNTS_PATH + "), page size " + pageSize);

            NativeLinkClient client = new NativeLinkClient(new IiqSessionClient(iiqConfig));
            NativeLinkRepository repository = new NativeLinkRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeLinkSink sink = new JdbcNativeLinkSink(conn, repository, nativeSchema);
                NativeLinkImportService svc = new NativeLinkImportService(client, sink, pageSize, true);
                NativeLinkImportService.Result result = svc.importAll();
                RunLedger.record("kf_account", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeAccountDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeAccountDbSummary(NativeLinkImportService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native accounts to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) Role/Bundle extraction: pull the plugin's REST endpoint over the existing
     * authenticated session and load native-rich rows into {@code iiq_native.kf_role}. IIQ-side
     * read-only; the only writes are to our PostgreSQL. Idempotent; full-scan deletion sweep.
     */
    private static int runExtractNativeIdentityRoleDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeIdentityRoleClient.IDENTITY_ROLES_PATH
                    + "), page size " + pageSize + " (pages over Identities)");
            com.keyforge.nativeload.NativeIdentityRoleClient client =
                    new com.keyforge.nativeload.NativeIdentityRoleClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeIdentityRoleRepository repository =
                    new com.keyforge.nativeload.NativeIdentityRoleRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeIdentityRoleSink sink =
                        new com.keyforge.nativeload.JdbcNativeIdentityRoleSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeIdentityRoleImportService.Result r =
                        new com.keyforge.nativeload.NativeIdentityRoleImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_identity_role", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " native identity-role edges to "
                        + repository.targetTable() + " (of " + r.getExtracted() + " extracted from "
                        + r.getScannedIdentities() + " Identities"
                        + (r.getSourceIdentityCount() < 0 ? "" : "/" + r.getSourceIdentityCount() + " total") + ")");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                if (r.isSweepRan()) {
                    System.out.println("  deletion sweep (soft): " + (r.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + r.getMarkedDeleted() + " deleted, revived " + r.getRevived())));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeRoleRelationshipsDb() {
        try {
            AppConfig iiq = AppConfig.load();
            PgConfig pg = PgConfig.load();
            String schema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiq.getBaseUrl() + " (user '" + iiq.getUsername() + "')");
            System.out.println("PostgreSQL: " + pg.getJdbcUrl() + " (user '" + pg.getUsername() + "', native schema '" + schema + "')");
            System.out.println("Source: native Bundle Profile/relationship APIs via plugin REST ("
                    + com.keyforge.nativeload.NativeRoleRelationshipClient.PATH + "), page size " + pageSize);
            com.keyforge.nativeload.NativeRoleRelationshipClient client =
                    new com.keyforge.nativeload.NativeRoleRelationshipClient(new IiqSessionClient(iiq));
            com.keyforge.nativeload.NativeRoleEntitlementRepository er = new com.keyforge.nativeload.NativeRoleEntitlementRepository(schema);
            com.keyforge.nativeload.NativeRoleHierarchyRepository hr = new com.keyforge.nativeload.NativeRoleHierarchyRepository(schema);
            try (Connection conn = PostgresConnection.open(pg)) {
                com.keyforge.nativeload.NativeRoleRelationshipImportService service =
                        new com.keyforge.nativeload.NativeRoleRelationshipImportService(client,
                                new com.keyforge.nativeload.JdbcNativeRoleRelationshipSink(conn, er, hr), pageSize);
                com.keyforge.nativeload.NativeRoleRelationshipImportService.Result r = service.run();
                int records = r.entitlements + r.hierarchy;
                RunLedger.record("kf_role_entitlement+kf_role_hierarchy", records,
                        r.entitlementInserted + r.hierarchyInserted, r.entitlementUpdated + r.hierarchyUpdated, r.failed);
                System.out.println();
                System.out.println("Scanned " + r.bundles + " native Bundles; persisted " + r.entitlements
                        + " role-profile edges and " + r.hierarchy + " role-hierarchy edges.");
                System.out.println("  role-entitlement inserted/updated: " + r.entitlementInserted + "/" + r.entitlementUpdated);
                System.out.println("  role-hierarchy inserted/updated:   " + r.hierarchyInserted + "/" + r.hierarchyUpdated);
                System.out.println("  failed: " + r.failed);
                System.out.println("  run ID: " + r.extractionRunId);
                System.out.println("  deletion sweep: " + (r.sweepsSkipped ? "skipped (empty source or row failure safety guard)"
                        : "completed; marked deleted " + r.entitlementDeleted + " role-profile and " + r.hierarchyDeleted + " hierarchy edges"));
                System.out.println("  targets: " + er.targetTable() + ", " + hr.targetTable());
                return r.failed > 0 ? 6 : 0;
            }
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage()); return 1;
        }
    }

    private static int runExtractNativeRoleDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeRoleClient.ROLES_PATH + "), page size " + pageSize);

            NativeRoleClient client = new NativeRoleClient(new IiqSessionClient(iiqConfig));
            NativeRoleRepository repository = new NativeRoleRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeRoleSink sink = new JdbcNativeRoleSink(conn, repository, nativeSchema);
                NativeRoleImportService svc = new NativeRoleImportService(client, sink, pageSize, true);
                NativeRoleImportService.Result result = svc.importAll();
                RunLedger.record("kf_role", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeRoleDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeRoleDbSummary(NativeRoleImportService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native roles to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /**
     * Native (Java-API) Workgroup extraction: pull the plugin's REST endpoint over the existing
     * authenticated session and load native-rich rows into {@code iiq_native.kf_workgroup}. Workgroups
     * are {@code Identity} objects flagged {@code workgroup==true} (IIQ has no dedicated Workgroup
     * class). IIQ-side read-only; the only writes are to our PostgreSQL. Idempotent; full-scan sweep.
     */
    private static int runExtractNativeWorkgroupDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeWorkgroupClient.WORKGROUPS_PATH + "), page size " + pageSize);

            NativeWorkgroupClient client = new NativeWorkgroupClient(new IiqSessionClient(iiqConfig));
            NativeWorkgroupRepository repository = new NativeWorkgroupRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeWorkgroupSink sink = new JdbcNativeWorkgroupSink(conn, repository, nativeSchema);
                NativeWorkgroupImportService svc = new NativeWorkgroupImportService(client, sink, pageSize, true);
                NativeWorkgroupImportService.Result result = svc.importAll();
                RunLedger.record("kf_workgroup", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeWorkgroupDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeWorkgroupDbSummary(NativeWorkgroupImportService.Result result, String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native workgroups to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractNativeWorkflowsDb() {
        try {
            AppConfig iiq = AppConfig.load(); PgConfig pg = PgConfig.load();
            String schema = NativeSchemaConfig.resolve(); int size = nativePageSize();
            var client = new com.keyforge.nativeload.NativeWorkflowClient(new IiqSessionClient(iiq), java.util.UUID.randomUUID().toString());
            var repository = new com.keyforge.nativeload.NativeWorkflowRepository(schema);
            try (Connection conn = PostgresConnection.open(pg)) {
                var result = new com.keyforge.nativeload.NativeWorkflowImportService(client, repository, size).run(conn);
                RunLedger.record("kf_workflow_definition", result.extracted, result.inserted, result.updated, result.failed);
                System.out.println("Native Workflow definitions: " + result.extracted + " extracted into " + repository.targetTable());
                System.out.println("  inserted=" + result.inserted + ", updated=" + result.updated + ", failed=" + result.failed
                        + ", deletion sweep=" + (result.sweepSkipped ? "skipped (empty source or failed rows)" : "marked " + result.marked + " deleted"));
                return result.failed == 0 ? 0 : 6;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException | IiqApiException e) { System.err.println("Native Workflow extraction error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeWorkgroupMembershipsDb() {
        try {
            AppConfig iiq = AppConfig.load(); PgConfig pg = PgConfig.load();
            String schema = NativeSchemaConfig.resolve(); int size = nativePageSize();
            String runId = java.util.UUID.randomUUID().toString();
            var client = new com.keyforge.nativeload.NativeWorkgroupMembershipClient(new IiqSessionClient(iiq), runId);
            var repository = new com.keyforge.nativeload.NativeWorkgroupMembershipRepository(schema);
            try (Connection conn = PostgresConnection.open(pg)) {
                var result = new com.keyforge.nativeload.NativeWorkgroupMembershipImportService(client, repository, size).run(conn);
                RunLedger.record("kf_workgroup_member", result.edges, result.inserted, result.updated, result.failed);
                System.out.println("Native Identity.getWorkgroups() memberships: " + result.edges + " source edges ("
                        + result.distinct + " distinct) into " + repository.targetTable());
                System.out.println("  inserted=" + result.inserted + ", updated=" + result.updated + ", failed=" + result.failed
                        + ", deletion sweep=" + (result.sweepSkipped ? "skipped (empty source or failed rows)" : "marked " + result.deleted + " deleted"));
                return result.failed == 0 ? 0 : 6;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException | IiqApiException e) { System.err.println("Native membership extraction error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /**
     * Native (Java-API) GroupDefinition extraction: pull the plugin's REST endpoint over the existing
     * authenticated session and load native-rich rows into {@code iiq_native.kf_group_definition}. Both
     * Populations and Groups are GroupDefinitions, distinguished by the source-backed {@code type}
     * (GROUP when a factory is present, POPULATION otherwise). IIQ-side read-only; the only writes are
     * to our PostgreSQL. Idempotent; full-scan deletion sweep.
     */
    private static int runExtractNativeGroupDefinitionDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST "
                    + "(" + NativeGroupDefinitionClient.GROUP_DEFINITIONS_PATH + "), page size " + pageSize);

            NativeGroupDefinitionClient client = new NativeGroupDefinitionClient(new IiqSessionClient(iiqConfig));
            NativeGroupDefinitionRepository repository = new NativeGroupDefinitionRepository(nativeSchema);

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                NativeGroupDefinitionSink sink = new JdbcNativeGroupDefinitionSink(conn, repository, nativeSchema);
                NativeGroupDefinitionImportService svc =
                        new NativeGroupDefinitionImportService(client, sink, pageSize, true);
                NativeGroupDefinitionImportService.Result result = svc.importAll();
                RunLedger.record("kf_group_definition", result.getExtracted(),
                        result.getInserted(), result.getUpdated(), result.getFailed());
                printNativeGroupDefinitionDbSummary(result, repository.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage());
            return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printNativeGroupDefinitionDbSummary(NativeGroupDefinitionImportService.Result result,
                                                            String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " native group definitions to " + targetTable + " (of " + result.getExtracted() + " extracted)");
        System.out.println("  groups:      " + result.getGroups());
        System.out.println("  populations: " + result.getPopulations());
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());
        if (result.isSweepRan()) {
            System.out.println("  deletion sweep: " + (result.isSweepSkipped()
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + result.getMarkedDeleted() + " deleted, revived " + result.getRevived())));
        }

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    /** Imports immutable native WorkItemArchive evidence; this source is append-only and has no sweep. */
    private static int runExtractNativeWorkItemArchiveDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeWorkItemArchiveClient.WORKITEM_ARCHIVES_PATH
                    + "), page size " + pageSize + "; append-only, no deletion sweep");
            com.keyforge.nativeload.NativeWorkItemArchiveClient client =
                    new com.keyforge.nativeload.NativeWorkItemArchiveClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeWorkItemArchiveRepository repository =
                    new com.keyforge.nativeload.NativeWorkItemArchiveRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeWorkItemArchiveSink sink =
                        new com.keyforge.nativeload.JdbcNativeWorkItemArchiveSink(conn, repository);
                com.keyforge.nativeload.NativeWorkItemArchiveImportService.Result r =
                        new com.keyforge.nativeload.NativeWorkItemArchiveImportService(client, sink, pageSize).importAll();
                RunLedger.record("kf_workitem_archive", r.getExtracted(), r.getInserted(), 0, r.getFailed());
                System.out.println();
                System.out.println("Scanned " + r.getExtracted() + " native WorkItemArchive records"
                        + (r.getSourceCount() < 0 ? "" : " (IIQ count " + r.getSourceCount() + ")"));
                System.out.println("  inserted: " + r.getInserted());
                System.out.println("  already present (deduped): " + r.getSkipped());
                System.out.println("  failed: " + r.getFailed());
                System.out.println("  deletion sweep: not applicable (append-only history)");
                System.out.println("  target: " + repository.targetTable());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) {
            System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage()); return 1;
        }
    }

    /** Native current-state IdentityEntitlement (identity↔entitlement with provenance). */
    private static int runExtractNativeIdentityEntitlementDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeIdentityEntitlementClient.IDENTITY_ENTITLEMENTS_PATH
                    + "), page size " + pageSize);

            com.keyforge.nativeload.NativeIdentityEntitlementClient client =
                    new com.keyforge.nativeload.NativeIdentityEntitlementClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeIdentityEntitlementRepository repository =
                    new com.keyforge.nativeload.NativeIdentityEntitlementRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeIdentityEntitlementSink sink =
                        new com.keyforge.nativeload.JdbcNativeIdentityEntitlementSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeIdentityEntitlementImportService.Result r =
                        new com.keyforge.nativeload.NativeIdentityEntitlementImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_identity_entitlement", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " native identity-entitlements to "
                        + repository.targetTable() + " (of " + r.getExtracted() + " extracted"
                        + (r.getSourceCount() < 0 ? "" : ", IIQ count " + r.getSourceCount()) + ")");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                if (r.isSweepRan()) {
                    System.out.println("  deletion sweep: " + (r.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + r.getMarkedDeleted() + " deleted, revived " + r.getRevived())));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native current-state account↔entitlement edges (from Link entitlement attributes). */
    private static int runExtractNativeAccountEntitlementDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeAccountEntitlementClient.ACCOUNT_ENTITLEMENTS_PATH
                    + "), page size " + pageSize + " (pages over Links)");

            com.keyforge.nativeload.NativeAccountEntitlementClient client =
                    new com.keyforge.nativeload.NativeAccountEntitlementClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeAccountEntitlementRepository repository =
                    new com.keyforge.nativeload.NativeAccountEntitlementRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeAccountEntitlementSink sink =
                        new com.keyforge.nativeload.JdbcNativeAccountEntitlementSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeAccountEntitlementImportService.Result r =
                        new com.keyforge.nativeload.NativeAccountEntitlementImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_account_entitlement", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " native account-entitlement edges to "
                        + repository.targetTable() + " (of " + r.getExtracted() + " extracted from "
                        + r.getScannedLinks() + " Links"
                        + (r.getSourceLinkCount() < 0 ? "" : "/" + r.getSourceLinkCount() + " total") + ")");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                if (r.isSweepRan()) {
                    System.out.println("  deletion sweep: " + (r.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + r.getMarkedDeleted() + " deleted, revived " + r.getRevived())));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native current-state Certification (campaign/cert container). */
    private static int runExtractNativeCertificationDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeCertificationClient.CERTIFICATIONS_PATH + "), page size " + pageSize);
            com.keyforge.nativeload.NativeCertificationClient client =
                    new com.keyforge.nativeload.NativeCertificationClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeCertificationRepository repository =
                    new com.keyforge.nativeload.NativeCertificationRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeCertificationSink sink =
                        new com.keyforge.nativeload.JdbcNativeCertificationSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeCertificationImportService.Result r =
                        new com.keyforge.nativeload.NativeCertificationImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_certification", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                printNativeCurrentStateSummary("certifications", r.getPersisted(), r.getExtracted(), r.getSourceCount(),
                        r.getInserted(), r.getUpdated(), r.getFailed(), r.isSweepRan(), r.isSweepSkipped(),
                        r.getMarkedDeleted(), r.getRevived(), repository.targetTable(), r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native current-state CertificationEntity (who/what was reviewed + entity action). */
    private static int runExtractNativeCertificationEntityDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeCertificationEntityClient.CERTIFICATION_ENTITIES_PATH
                    + "), page size " + pageSize);
            com.keyforge.nativeload.NativeCertificationEntityClient client =
                    new com.keyforge.nativeload.NativeCertificationEntityClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeCertificationEntityRepository repository =
                    new com.keyforge.nativeload.NativeCertificationEntityRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeCertificationEntitySink sink =
                        new com.keyforge.nativeload.JdbcNativeCertificationEntitySink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeCertificationEntityImportService.Result r =
                        new com.keyforge.nativeload.NativeCertificationEntityImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_certification_entity", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                printNativeCurrentStateSummary("certification entities", r.getPersisted(), r.getExtracted(),
                        r.getSourceCount(), r.getInserted(), r.getUpdated(), r.getFailed(), r.isSweepRan(),
                        r.isSweepSkipped(), r.getMarkedDeleted(), r.getRevived(), repository.targetTable(), r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native current-state CertificationItem (reviewed item + folded decision/action). */
    private static int runExtractNativeCertificationItemDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeCertificationItemClient.CERTIFICATION_ITEMS_PATH
                    + "), page size " + pageSize);
            com.keyforge.nativeload.NativeCertificationItemClient client =
                    new com.keyforge.nativeload.NativeCertificationItemClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeCertificationItemRepository repository =
                    new com.keyforge.nativeload.NativeCertificationItemRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeCertificationItemSink sink =
                        new com.keyforge.nativeload.JdbcNativeCertificationItemSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeCertificationItemImportService.Result r =
                        new com.keyforge.nativeload.NativeCertificationItemImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_certification_item", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                printNativeCurrentStateSummary("certification items", r.getPersisted(), r.getExtracted(),
                        r.getSourceCount(), r.getInserted(), r.getUpdated(), r.getFailed(), r.isSweepRan(),
                        r.isSweepSkipped(), r.getMarkedDeleted(), r.getRevived(), repository.targetTable(), r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native append-only CertificationArchive (immutable historical certifications; no deletion sweep). */
    private static int runExtractNativeCertificationArchiveDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeCertificationArchiveClient.CERTIFICATION_ARCHIVES_PATH
                    + "), page size " + pageSize);
            com.keyforge.nativeload.NativeCertificationArchiveClient client =
                    new com.keyforge.nativeload.NativeCertificationArchiveClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeCertificationArchiveRepository repository =
                    new com.keyforge.nativeload.NativeCertificationArchiveRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeCertificationArchiveSink sink =
                        new com.keyforge.nativeload.JdbcNativeCertificationArchiveSink(conn, repository);
                com.keyforge.nativeload.NativeCertificationArchiveImportService.Result r =
                        new com.keyforge.nativeload.NativeCertificationArchiveImportService(client, sink, pageSize).importAll();
                RunLedger.record("kf_certification_archive", r.getExtracted(), r.getInserted(), 0, r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " native certification archives to "
                        + repository.targetTable() + " (of " + r.getExtracted() + " extracted"
                        + (r.getSourceCount() < 0 ? "" : ", IIQ count " + r.getSourceCount()) + ")");
                System.out.println("  inserted: " + r.getInserted() + "  already present (deduped): " + r.getSkipped()
                        + "  failed: " + r.getFailed());
                System.out.println("  deletion sweep: not applicable (append-only history)");
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Shared summary printer for the native current-state relationship/certification imports. */
    private static void printNativeCurrentStateSummary(String label, int persisted, int extracted, int sourceCount,
                                                       int inserted, int updated, int failed, boolean sweepRan,
                                                       boolean sweepSkipped, int marked, int revived,
                                                       String targetTable, List<String> failures) {
        System.out.println();
        System.out.println("Persisted " + persisted + " native " + label + " to " + targetTable
                + " (of " + extracted + " extracted" + (sourceCount < 0 ? "" : ", IIQ count " + sourceCount) + ")");
        System.out.println("  inserted: " + inserted + "  updated: " + updated + "  failed: " + failed);
        if (sweepRan) {
            System.out.println("  deletion sweep: " + (sweepSkipped
                    ? "skipped (empty source — safety guard)"
                    : ("marked " + marked + " deleted, revived " + revived)));
        }
        printLines(failures);
    }

    /** Native current-state ProvisioningTransaction (+ derived provisioning items) into iiq_native. */
    /** Native current-state WorkItem (live/pending work items, incl. in-flight approvals) into iiq_native. */
    private static int runExtractNativeWorkItemDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeWorkItemClient.WORKITEMS_PATH + "), page size " + pageSize);
            com.keyforge.nativeload.NativeWorkItemClient client =
                    new com.keyforge.nativeload.NativeWorkItemClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeWorkItemRepository repository =
                    new com.keyforge.nativeload.NativeWorkItemRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeWorkItemSink sink =
                        new com.keyforge.nativeload.JdbcNativeWorkItemSink(conn, repository, nativeSchema);
                com.keyforge.nativeload.NativeWorkItemImportService.Result r =
                        new com.keyforge.nativeload.NativeWorkItemImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_workitem", r.getExtracted(), r.getInserted(), r.getUpdated(), r.getFailed());
                printNativeCurrentStateSummary("work items", r.getPersisted(), r.getExtracted(), r.getSourceCount(),
                        r.getInserted(), r.getUpdated(), r.getFailed(), r.isSweepRan(), r.isSweepSkipped(),
                        r.getMarkedDeleted(), r.getRevived(), repository.targetTable(), r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    /** Native current-state IdentityRequest (+ derived items + request-time approval summaries) into iiq_native. */
    private static int runExtractNativeIdentityRequestDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeIdentityRequestClient.IDENTITY_REQUESTS_PATH
                    + "), page size " + pageSize);
            com.keyforge.nativeload.NativeIdentityRequestClient client =
                    new com.keyforge.nativeload.NativeIdentityRequestClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeIdentityRequestRepository reqRepo =
                    new com.keyforge.nativeload.NativeIdentityRequestRepository(nativeSchema);
            com.keyforge.nativeload.NativeIdentityRequestItemRepository itemRepo =
                    new com.keyforge.nativeload.NativeIdentityRequestItemRepository(nativeSchema);
            com.keyforge.nativeload.NativeIdentityRequestApprovalRepository apprRepo =
                    new com.keyforge.nativeload.NativeIdentityRequestApprovalRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeIdentityRequestSink sink =
                        new com.keyforge.nativeload.JdbcNativeIdentityRequestSink(conn, reqRepo, itemRepo, apprRepo, nativeSchema);
                com.keyforge.nativeload.NativeIdentityRequestImportService.Result r =
                        new com.keyforge.nativeload.NativeIdentityRequestImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_identity_request", r.getRequests(), r.getRequestInserted(), r.getRequestUpdated(), r.getFailed());
                RunLedger.record("kf_identity_request_item", r.getItems(), r.getItemInserted(), r.getItemUpdated(), 0);
                RunLedger.record("kf_identity_request_approval", r.getApprovals(), r.getApprovalInserted(), r.getApprovalUpdated(), 0);
                System.out.println();
                System.out.println("Persisted " + r.getRequestPersisted() + " native identity requests to "
                        + reqRepo.targetTable() + " (of " + r.getRequests() + " extracted"
                        + (r.getSourceCount() < 0 ? "" : ", IIQ count " + r.getSourceCount()) + ")");
                System.out.println("  request inserted: " + r.getRequestInserted() + "  updated: " + r.getRequestUpdated());
                System.out.println("Persisted " + r.getItemPersisted() + " native request items to " + itemRepo.targetTable()
                        + " (of " + r.getItems() + " derived); inserted " + r.getItemInserted() + ", updated " + r.getItemUpdated());
                System.out.println("Persisted " + r.getApprovalPersisted() + " native request approvals to " + apprRepo.targetTable()
                        + " (of " + r.getApprovals() + " derived); inserted " + r.getApprovalInserted() + ", updated " + r.getApprovalUpdated());
                System.out.println("  failed: " + r.getFailed());
                if (r.isSweepRan()) {
                    System.out.println("  deletion sweep (soft): " + (r.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + r.getRequestMarkedDeleted() + " requests / " + r.getItemMarkedDeleted()
                                + " items / " + r.getApprovalMarkedDeleted() + " approvals deleted")));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractNativeProvisioningTxnDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            int pageSize = nativePageSize();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl() + " (user '" + pgConfig.getUsername()
                    + "', native schema '" + nativeSchema + "')");
            System.out.println("Source: native SailPoint Java API via plugin REST ("
                    + com.keyforge.nativeload.NativeProvisioningTxnClient.PROVISIONING_TRANSACTIONS_PATH
                    + "), page size " + pageSize);
            com.keyforge.nativeload.NativeProvisioningTxnClient client =
                    new com.keyforge.nativeload.NativeProvisioningTxnClient(new IiqSessionClient(iiqConfig));
            com.keyforge.nativeload.NativeProvisioningTxnRepository txnRepo =
                    new com.keyforge.nativeload.NativeProvisioningTxnRepository(nativeSchema);
            com.keyforge.nativeload.NativeProvisioningItemRepository itemRepo =
                    new com.keyforge.nativeload.NativeProvisioningItemRepository(nativeSchema);
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.nativeload.NativeProvisioningTxnSink sink =
                        new com.keyforge.nativeload.JdbcNativeProvisioningTxnSink(conn, txnRepo, itemRepo, nativeSchema);
                com.keyforge.nativeload.NativeProvisioningTxnImportService.Result r =
                        new com.keyforge.nativeload.NativeProvisioningTxnImportService(client, sink, pageSize, true).importAll();
                RunLedger.record("kf_provisioning_txn", r.getTxns(), r.getTxnInserted(), r.getTxnUpdated(), r.getFailed());
                RunLedger.record("kf_provisioning_item", r.getItems(), r.getItemInserted(), r.getItemUpdated(), 0);
                System.out.println();
                System.out.println("Persisted " + r.getTxnPersisted() + " native provisioning transactions to "
                        + txnRepo.targetTable() + " (of " + r.getTxns() + " extracted"
                        + (r.getSourceCount() < 0 ? "" : ", IIQ count " + r.getSourceCount()) + ")");
                System.out.println("  txn inserted: " + r.getTxnInserted() + "  updated: " + r.getTxnUpdated());
                System.out.println("Persisted " + r.getItemPersisted() + " native provisioning items to "
                        + itemRepo.targetTable() + " (of " + r.getItems() + " derived)");
                System.out.println("  item inserted: " + r.getItemInserted() + "  updated: " + r.getItemUpdated());
                System.out.println("  failed: " + r.getFailed());
                if (r.isSweepRan()) {
                    System.out.println("  deletion sweep (soft): " + (r.isSweepSkipped()
                            ? "skipped (empty source — safety guard)"
                            : ("marked " + r.getTxnMarkedDeleted() + " txns / " + r.getItemMarkedDeleted()
                                + " items deleted")));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (NativeImportException e) { System.err.println("Native payload error: " + e.getMessage()); return 4;
        } catch (IiqApiException e) { System.err.println("IdentityIQ plugin API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractUsersDb() {
        try {
            // 1. Load IIQ + PostgreSQL configuration (both fail clearly if incomplete).
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // 2-4. Extract all users from IdentityIQ (unchanged, verified service).
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            System.out.println("Extracted " + identities.size() + " identities from IdentityIQ");

            // 5-6. Map and persist into the configured schema's kf_identity table.
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<Identity> plan = planExtraction(
                        conn, schema, "kf_identity", "meta.lastModified", identities, Main::identityChangeTime);
                UserPersistenceService svc = new UserPersistenceService(schema);
                UserPersistenceService.Result result = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_identity", plan.kept(), result.getInserted(), result.getUpdated(), result.getFailed());
                advanceWatermark(conn, schema, "kf_identity", "meta.lastModified", plan, result.getFailed());
                sweepEntityDeletions(conn, schema, "kf_identity", "userid", identities, Identity::getId);
                printUserDbSummary(plan.kept(), result, svc.targetTable());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUserDbSummary(int extracted, UserPersistenceService.Result result,
                                           String targetTable) {
        System.out.println();
        System.out.println("Successfully persisted " + result.getPersisted()
                + " users to " + targetTable + " (of " + extracted + " extracted)");
        System.out.println("  inserted: " + result.getInserted());
        System.out.println("  updated:  " + result.getUpdated());
        System.out.println("  failed:   " + result.getFailed());

        int shown = Math.min(result.getFailures().size(), 10);
        for (int i = 0; i < shown; i++) {
            System.out.println("    - " + result.getFailures().get(i));
        }
        if (result.getFailures().size() > shown) {
            System.out.println("    ... and " + (result.getFailures().size() - shown) + " more");
        }
    }

    private static int runExtractApplications() {
        try {
            // 1. Configuration (fails clearly if anything is missing).
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            // 2. Reuse the same HTTP client + 3. create the service.
            IiqApiClient client = new IiqApiClient(config);
            ApplicationService applicationService = new ApplicationService(client);

            // 4. Retrieve all applications (paginated).
            List<Application> applications = applicationService.getAllApplications();

            // 5. Summary + lightweight verification output.
            printApplicationSummary(applications);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractAssignments() {
        try {
            // 1. Configuration (fails clearly if anything is missing).
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            // 2. Reuse the same HTTP client for every source extraction.
            IiqApiClient client = new IiqApiClient(config);

            // 3. Gather the source data using the existing, verified services.
            System.out.println("Extracting accounts, entitlements and identities...");
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            System.out.printf("  sources: %d accounts, %d entitlements, %d identities%n",
                    accounts.size(), entitlements.size(), identities.size());

            // 4. Transform into Account -> Entitlement assignments (no HTTP here).
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);

            // 5. Summary + lightweight verification output.
            printAssignmentSummary(assignments);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printAssignmentSummary(List<AccountEntitlementAssignment> assignments) {
        System.out.println();
        System.out.println("Successfully derived " + assignments.size() + " account-entitlement assignments");

        long resolved = assignments.stream().filter(AccountEntitlementAssignment::isResolved).count();
        long unresolved = assignments.size() - resolved;
        long accountOnly = assignments.stream()
                .filter(a -> a.getResolutionSources().size() == 1
                        && a.getResolutionSources().contains(
                                AccountEntitlementAssignment.ResolutionSource.ACCOUNT_ATTRIBUTE))
                .count();
        long userExtOnly = assignments.stream()
                .filter(a -> a.getResolutionSources().size() == 1
                        && a.getResolutionSources().contains(
                                AccountEntitlementAssignment.ResolutionSource.USER_EXTENSION))
                .count();
        long validated = assignments.stream()
                .filter(a -> a.getResolutionSources().size() > 1)
                .count();
        long distinctAccounts = assignments.stream()
                .map(AccountEntitlementAssignment::getAccountId)
                .filter(Objects::nonNull).distinct().count();
        long distinctEntitlements = assignments.stream()
                .map(AccountEntitlementAssignment::getEntitlementId)
                .filter(Objects::nonNull).distinct().count();
        long unmatchedAccount = assignments.stream()
                .filter(a -> a.getAccountId() == null).count();

        System.out.println("  resolved to a catalogue entitlement: " + resolved);
        System.out.println("  unresolved (kept, entitlementId=null): " + unresolved);
        System.out.println("  distinct accounts: " + distinctAccounts
                + ", distinct entitlements: " + distinctEntitlements);
        System.out.println("  source = account attribute only: " + accountOnly);
        System.out.println("  source = user extension only: " + userExtOnly);
        System.out.println("  validated by both sources: " + validated);
        if (unmatchedAccount > 0) {
            System.out.println("  user-extension entries without a matching account: " + unmatchedAccount);
        }

        if (assignments.isEmpty()) {
            return;
        }

        int sampleSize = Math.min(SAMPLE_SIZE, assignments.size());
        System.out.println();
        System.out.println("Sample (first " + sampleSize + "):");
        for (int i = 0; i < sampleSize; i++) {
            AccountEntitlementAssignment a = assignments.get(i);
            String ent = a.getEntitlementId() == null ? "UNRESOLVED" : a.getEntitlementId();
            System.out.printf("  - account=%s application=%s %s=%s -> entitlement=%s%n",
                    a.getAccountName(),
                    a.getApplicationName(),
                    a.getSourceAttribute(),
                    a.getEntitlementValue(),
                    ent);
        }
    }

    private static int runExtractAccounts() {
        try {
            // 1. Configuration (fails clearly if anything is missing).
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            // 2. Reuse the same HTTP client + 3. create the service.
            IiqApiClient client = new IiqApiClient(config);
            AccountService accountService = new AccountService(client);

            // 4. Retrieve all accounts (paginated).
            List<Account> accounts = accountService.getAllAccounts();

            // 5. Summary + lightweight verification output.
            printAccountSummary(accounts);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printAccountSummary(List<Account> accounts) {
        System.out.println();
        System.out.println("Successfully retrieved " + accounts.size() + " accounts");

        long withIdentity = accounts.stream().filter(a -> a.getIdentity() != null).count();
        long withApplication = accounts.stream().filter(a -> a.getApplication() != null).count();
        long withEntitlements = accounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getHasEntitlements()))
                .count();
        System.out.println("  with an identity reference: " + withIdentity);
        System.out.println("  with an application reference: " + withApplication);
        System.out.println("  flagged hasEntitlements: " + withEntitlements);

        if (accounts.isEmpty()) {
            return;
        }

        int sampleSize = Math.min(SAMPLE_SIZE, accounts.size());
        System.out.println();
        System.out.println("Sample (first " + sampleSize + "):");
        for (int i = 0; i < sampleSize; i++) {
            Account acc = accounts.get(i);
            String appLabel = acc.getApplication() == null ? "-" : acc.getApplication().getDisplayName();
            String idLabel = acc.getIdentity() == null ? "-" : acc.getIdentity().getDisplayName();
            // nativeIdentity is a source key, not a credential; safe to print.
            System.out.printf("  - nativeIdentity=%s application=%s identity=%s hasEntitlements=%s extraAttrs=%d%n",
                    acc.getNativeIdentity(),
                    appLabel,
                    idLabel,
                    acc.getHasEntitlements(),
                    acc.getAdditionalAttributes().size());
        }
    }

    private static int runExtractEntitlements() {
        try {
            // 1. Configuration (fails clearly if anything is missing).
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            // 2. Reuse the same HTTP client + 3. create the service.
            IiqApiClient client = new IiqApiClient(config);
            EntitlementService entitlementService = new EntitlementService(client);

            // 4. Retrieve all entitlements (paginated).
            List<Entitlement> entitlements = entitlementService.getAllEntitlements();

            // 5. Summary + lightweight verification output.
            printEntitlementSummary(entitlements);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static void printEntitlementSummary(List<Entitlement> entitlements) {
        System.out.println();
        System.out.println("Successfully retrieved " + entitlements.size() + " entitlements");

        long requestable = entitlements.stream()
                .filter(e -> Boolean.TRUE.equals(e.getRequestable()))
                .count();
        long withApplication = entitlements.stream()
                .filter(e -> e.getApplication() != null)
                .count();
        System.out.println("  requestable: " + requestable);
        System.out.println("  linked to an application: " + withApplication);

        if (entitlements.isEmpty()) {
            return;
        }

        int sampleSize = Math.min(SAMPLE_SIZE, entitlements.size());
        System.out.println();
        System.out.println("Sample (first " + sampleSize + "):");
        for (int i = 0; i < sampleSize; i++) {
            Entitlement ent = entitlements.get(i);
            String appLabel = ent.getApplication() == null ? "-" : ent.getApplication().getDisplayName();
            System.out.printf("  - displayableName=%s value=%s type=%s attribute=%s application=%s%n",
                    ent.getDisplayableName(),
                    ent.getValue(),
                    ent.getType(),
                    ent.getAttribute(),
                    appLabel);
        }
    }

    private static void printApplicationSummary(List<Application> applications) {
        System.out.println();
        System.out.println("Successfully retrieved " + applications.size() + " applications");

        long withSchemas = applications.stream()
                .filter(a -> a.getApplicationSchemaCount() > 0)
                .count();
        System.out.println("  with application schemas: " + withSchemas);

        if (applications.isEmpty()) {
            return;
        }

        int sampleSize = Math.min(SAMPLE_SIZE, applications.size());
        System.out.println();
        System.out.println("Sample (first " + sampleSize + "):");
        for (int i = 0; i < sampleSize; i++) {
            Application app = applications.get(i);
            String ownerLabel = app.getOwner() == null ? "-" : app.getOwner().getDisplayName();
            System.out.printf("  - name=%s type=%s owner=%s schemas=%d%n",
                    app.getName(),
                    app.getType(),
                    ownerLabel,
                    app.getApplicationSchemaCount());
        }
    }

    private static void printSummary(List<Identity> identities) {
        System.out.println();
        System.out.println("Successfully retrieved " + identities.size() + " identities");

        long activeCount = identities.stream()
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .count();
        long withAccounts = identities.stream()
                .filter(i -> i.getAccountReferenceCount() > 0)
                .count();
        long totalAccountRefs = identities.stream()
                .mapToLong(Identity::getAccountReferenceCount)
                .sum();

        System.out.println("  active: " + activeCount);
        System.out.println("  with account references: " + withAccounts
                + " (" + totalAccountRefs + " account references in total)");

        if (identities.isEmpty()) {
            return;
        }

        int sampleSize = Math.min(SAMPLE_SIZE, identities.size());
        System.out.println();
        System.out.println("Sample (first " + sampleSize + "):");
        for (int i = 0; i < sampleSize; i++) {
            Identity id = identities.get(i);
            // Deliberately avoids printing email/PII; enough to confirm extraction worked.
            System.out.printf("  - id=%s userName=%s displayName=%s active=%s accounts=%d%n",
                    id.getId(),
                    id.getUserName(),
                    id.getDisplayName(),
                    id.getActive(),
                    id.getAccountReferenceCount());
        }
    }

    private static int runExtractRoles() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            IiqApiClient client = new IiqApiClient(config);
            List<Role> roles = new RoleService(client).getAllRoles();

            System.out.println();
            System.out.println("Extracted " + roles.size() + " roles from IdentityIQ");
            int shown = Math.min(roles.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                Role r = roles.get(i);
                String type = r.getType() == null ? null : r.getType().getName();
                String owner = r.getOwner() == null ? null : r.getOwner().getDisplayName();
                System.out.println("  - " + r.getDisplayableName()
                        + " [type=" + type + ", active=" + r.getActive() + ", owner=" + owner + "]");
            }

            // Preview the role->role edges we would derive, without touching the database.
            int edges = 0;
            for (Role r : roles) {
                edges += RoleHierarchyRowMapper.mapAll(r).size();
            }
            System.out.println("Role hierarchy edges derivable (inherits/requires/permits): " + edges);
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractRolesDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Role> roles = new RoleService(client).getAllRoles();
            System.out.println("Extracted " + roles.size() + " roles from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<Role> plan = planExtraction(
                        conn, schema, "kf_role", "meta.lastModified", roles, Main::roleChangeTime);
                RolePersistenceService svc = new RolePersistenceService(schema);
                RolePersistenceService.Result result = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_role", plan.kept(), result.getInserted(), result.getUpdated(), result.getFailed());
                advanceWatermark(conn, schema, "kf_role", "meta.lastModified", plan, result.getFailed());
                sweepEntityDeletions(conn, schema, "kf_role", "roleid", roles, Role::getId);
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " roles to " + svc.targetTable() + " (of " + plan.kept() + " selected, "
                        + roles.size() + " extracted)");
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated:  " + result.getUpdated());
                System.out.println("  failed:   " + result.getFailed());
                printLines(result.getFailures());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractRoleHierarchyDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Role> roles = new RoleService(client).getAllRoles();
            System.out.println("Extracted " + roles.size() + " roles from IdentityIQ");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                RoleHierarchyPersistenceService svc = new RoleHierarchyPersistenceService(pgConfig.getSchema());
                RoleHierarchyPersistenceService.Result result = svc.persist(conn, roles);
                RunLedger.record("kf_role_hierarchy", result.getPersisted() + result.getFailed(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Persisted " + result.getPersisted()
                        + " role hierarchy edges to " + svc.targetTable());
                System.out.println("  derived:  " + result.getDerived());
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated:  " + result.getUpdated());
                System.out.println("  failed:   " + result.getFailed());
                if (result.getDerived() == 0) {
                    System.out.println("  (0 edges is expected: the current roles carry no "
                            + "inheritance/requirements/permits)");
                }
                printLines(result.getFailures());
                // CSS deletion detection: rebuild the keep-set from the SAME derivation persistence uses
                // (RoleHierarchyRowMapper.mapAll flattened over the full getAllRoles() pull) and sweep by the
                // deterministic hierarchyid, so the keep-set equals the persisted PKs exactly. Single full pull
                // (throws on failure -> sweep never runs on a partial source); empty-source guarded, so 0 edges
                // NEVER causes mass soft-deletion. A removed inherits/requires/permits edge -> its hierarchyid
                // is absent -> marked deleted; a reappearing edge is revived.
                List<com.keyforge.iiq.role.RoleHierarchyRow> hierarchyKeepRows = new ArrayList<>();
                for (Role r : roles) {
                    hierarchyKeepRows.addAll(RoleHierarchyRowMapper.mapAll(r));
                }
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_role_hierarchy", "hierarchyid",
                        hierarchyKeepRows, com.keyforge.iiq.role.RoleHierarchyRow::hierarchyid);
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractWorkgroups() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqApiClient client = new IiqSessionClient(config, debugHttp);
            UserGroupService.Result extraction = new UserGroupService(client).extractAll();

            List<UserGroup> all = extraction.getUserGroups();
            List<UserGroup> workgroups = WorkgroupPersistenceService.workgroupsOnly(all);
            System.out.println();
            System.out.println("Extracted " + workgroups.size() + " Workgroups (of " + all.size()
                    + " user-group records; Populations/Groups excluded from kf_workgroup)");

            int shown = Math.min(workgroups.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                WorkgroupRow row = WorkgroupRowMapper.map(workgroups.get(i));
                System.out.println("  - " + row.name() + " [id=" + row.workgroupid()
                        + ", modified=" + row.modifiedAt() + ", owner_id=" + row.ownerId()
                        + ", status=" + row.status() + ", member_count=" + row.memberCount() + "]");
            }
            System.out.println("(owner/status/created/member_count are NULL: the live Workgroup "
                    + "DataSource provides only id/name/description/modified)");
            if (extraction.anyEndpointFailed()) {
                for (String note : extraction.getSourceStatus()) {
                    System.out.println("  " + note);
                }
                return 4;
            }
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractWorkgroupsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqApiClient client = new IiqSessionClient(iiqConfig, debugHttp);
            UserGroupService.Result extraction = new UserGroupService(client).extractAll();

            List<UserGroup> all = extraction.getUserGroups();
            System.out.println("Extracted " + all.size() + " user-group records ("
                    + countByType(all, UserGroupService.TYPE_WORKGROUP) + " Workgroups, "
                    + countByType(all, UserGroupService.TYPE_POPULATION) + " Populations, "
                    + countByType(all, UserGroupService.TYPE_GROUP) + " Groups)");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                WorkgroupPersistenceService svc = new WorkgroupPersistenceService(pgConfig.getSchema());
                WorkgroupPersistenceService.Result result = svc.persist(conn, all);
                RunLedger.record("kf_workgroup", result.getWorkgroups(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " Workgroups to " + svc.targetTable() + " (of " + result.getWorkgroups() + " Workgroups)");
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated:  " + result.getUpdated());
                System.out.println("  failed:   " + result.getFailed());
                printLines(result.getFailures());
                // CSS deletion detection: mark Workgroups no longer in the full source subset as deleted.
                sweepEntityDeletions(conn, pgConfig.getSchema(), "kf_workgroup", "workgroupid",
                        WorkgroupPersistenceService.workgroupsOnly(all), UserGroup::getId);
                if (result.getFailed() > 0) {
                    return 6;
                }
                return extraction.anyEndpointFailed() ? 4 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractObjectOwners() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            IiqApiClient client = new IiqApiClient(config);
            List<Application> apps = new ApplicationService(client).getAllApplications();
            List<Role> roles = new RoleService(client).getAllRoles();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();

            List<ObjectOwnerRow> rows = ObjectOwnerPersistenceService.buildRows(apps, roles, entitlements);
            long appOwners = rows.stream().filter(r -> "Application".equals(r.objectType())).count();
            long roleOwners = rows.stream().filter(r -> "Role".equals(r.objectType())).count();
            long entOwners = rows.stream().filter(r -> "Entitlement".equals(r.objectType())).count();

            System.out.println();
            System.out.println("Object-owner edges (owner relationships): " + rows.size()
                    + "  [Application=" + appOwners + " of " + apps.size()
                    + ", Role=" + roleOwners + " of " + roles.size()
                    + ", Entitlement=" + entOwners + " of " + entitlements.size() + "]");
            int shown = Math.min(rows.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                ObjectOwnerRow r = rows.get(i);
                System.out.println("  - " + r.objectType() + " '" + r.objectName()
                        + "' owned by " + r.ownerDisplayName() + " (" + r.ownerId() + ")");
            }
            System.out.println("(only the 'owner' role is sourceable; revoker/remediator/certifier "
                    + "have no authoritative source and are not emitted)");
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractObjectOwnersDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Application> apps = new ApplicationService(client).getAllApplications();
            List<Role> roles = new RoleService(client).getAllRoles();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            System.out.println("Extracted sources: " + apps.size() + " applications, "
                    + roles.size() + " roles, " + entitlements.size() + " entitlements");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ObjectOwnerPersistenceService svc = new ObjectOwnerPersistenceService(pgConfig.getSchema());
                ObjectOwnerPersistenceService.Result result = svc.persist(conn, apps, roles, entitlements);
                RunLedger.record("kf_object_owner", result.getDerived(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " object-owner edges to " + svc.targetTable() + " (of " + result.getDerived() + " derived)");
                System.out.println("  Application owners: " + result.getApplicationOwners());
                System.out.println("  Role owners:        " + result.getRoleOwners());
                System.out.println("  Entitlement owners: " + result.getEntitlementOwners());
                System.out.println("  inserted: " + result.getInserted());
                System.out.println("  updated:  " + result.getUpdated());
                System.out.println("  failed:   " + result.getFailed());
                printLines(result.getFailures());
                // CSS deletion detection: rebuild the keep-set from the SAME pure derivation persistence
                // uses (buildRows over the full apps/roles/entitlements pulls). ownerid is deterministic,
                // so the keep-set equals the persisted ownerids exactly. Three single full pulls (each
                // throws on failure -> sweep never runs on a partial source); empty-source guarded. This
                // also handles an owner CHANGE (old edge id absent -> deleted; new edge id -> upserted).
                List<com.keyforge.iiq.objectowner.ObjectOwnerRow> ownerKeepRows =
                        com.keyforge.iiq.objectowner.ObjectOwnerPersistenceService.buildRows(apps, roles, entitlements);
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_object_owner", "ownerid", ownerKeepRows,
                        com.keyforge.iiq.objectowner.ObjectOwnerRow::ownerid);
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractAccountEntitlements() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            IiqApiClient client = new IiqApiClient(config);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);

            List<AccountEntitlementRow> rows = assignments.stream()
                    .map(AccountEntitlementRowMapper::map).toList();
            long resolved = rows.stream().filter(r -> "RESOLVED".equals(r.resolutionStatus())).count();
            System.out.println();
            System.out.println("Projected " + rows.size() + " account-entitlement edges "
                    + "(resolved=" + resolved + ", unresolved=" + (rows.size() - resolved)
                    + ") from " + accounts.size() + " accounts, " + entitlements.size() + " entitlements");
            int shown = Math.min(rows.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                AccountEntitlementRow r = rows.get(i);
                System.out.println("  - account " + r.accountNativeName() + " [" + r.applicationName() + "] "
                        + r.sourceAttribute() + "=" + r.entitlementValue()
                        + " -> entitlement_id=" + r.entitlementId() + " (" + r.resolutionStatus() + ")");
            }
            System.out.println("(reused existing derivation; no new IdentityIQ source)");
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractAccountEntitlementsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Reuse the existing, verified extraction + derivation (unchanged) — same input that
            // feeds entitlementassignment. kf_account_entitlement is a normalized projection of it.
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);
            System.out.println("Derived " + assignments.size() + " account-entitlement relationships"
                    + " (from " + accounts.size() + " accounts, " + entitlements.size() + " entitlements)");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                AccountEntitlementPersistenceService svc =
                        new AccountEntitlementPersistenceService(pgConfig.getSchema());
                AccountEntitlementPersistenceService.Result result = svc.persist(conn, assignments);
                RunLedger.record("kf_account_entitlement", result.getDerived(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " account-entitlement edges to " + svc.targetTable() + " (of " + result.getDerived() + " derived)");
                System.out.println("  resolved:   " + result.getResolved());
                System.out.println("  unresolved: " + result.getUnresolved());
                System.out.println("  inserted:   " + result.getInserted());
                System.out.println("  updated:    " + result.getUpdated());
                System.out.println("  failed:     " + result.getFailed());
                printLines(result.getFailures());
                // CSS deletion detection: keep-set = the SAME mapper persistence uses, over the SAME
                // assignments (pure build over the full accounts/entitlements/identities pulls). map() is
                // unconditional, so every persisted id is reproduced exactly. Three single full pulls ->
                // no partial-fetch (each throws on failure -> sweep never runs on a partial); empty-guarded.
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_account_entitlement", "id", assignments,
                        a -> com.keyforge.iiq.accountentitlement.AccountEntitlementRowMapper.map(a).id());
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractIdentityEntitlements() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            IiqApiClient client = new IiqApiClient(config);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);

            List<IdentityEntitlementRow> rows =
                    IdentityEntitlementPersistenceService.buildDistinctRows(assignments);
            long resolved = rows.stream().filter(r -> "RESOLVED".equals(r.resolutionStatus())).count();
            System.out.println();
            System.out.println("Projected " + rows.size() + " distinct identity-entitlement edges "
                    + "(resolved=" + resolved + ", unresolved=" + (rows.size() - resolved) + ") from "
                    + assignments.size() + " account-entitlement assignments");
            int shown = Math.min(rows.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                IdentityEntitlementRow r = rows.get(i);
                System.out.println("  - identity " + r.identityDisplayName() + " -> "
                        + r.entitlementValue() + " [" + r.applicationName() + "] (" + r.resolutionStatus() + ")");
            }
            System.out.println("(projection of existing derivation; NOT authoritative IdentityEntitlement provenance "
                    + "-> source/assigner/dates/aggregation_state/granted_by_role are NULL)");
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractIdentityEntitlementsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // Reuse the existing, verified extraction + derivation (unchanged). This is a projection
            // of the identity->account->entitlement relationship already in that derivation.
            IiqApiClient client = new IiqApiClient(iiqConfig);
            List<Account> accounts = new AccountService(client).getAllAccounts();
            List<Entitlement> entitlements = new EntitlementService(client).getAllEntitlements();
            List<Identity> identities = new IdentityService(client).getAllIdentities();
            List<AccountEntitlementAssignment> assignments =
                    new AccountEntitlementAssignmentService().build(accounts, entitlements, identities);
            System.out.println("Derived " + assignments.size() + " account-entitlement assignments"
                    + " (from " + accounts.size() + " accounts, " + entitlements.size() + " entitlements)");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                IdentityEntitlementPersistenceService svc =
                        new IdentityEntitlementPersistenceService(pgConfig.getSchema());
                IdentityEntitlementPersistenceService.Result result = svc.persist(conn, assignments);
                RunLedger.record("kf_identity_entitlement", result.getDistinctEdges(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " identity-entitlement edges to " + svc.targetTable()
                        + " (of " + result.getDistinctEdges() + " distinct edges)");
                System.out.println("  input assignments:    " + result.getInputAssignments());
                System.out.println("  skipped (no identity):" + result.getSkippedNoIdentity());
                System.out.println("  duplicates collapsed: " + result.getDuplicatesCollapsed());
                System.out.println("  distinct edges:       " + result.getDistinctEdges());
                System.out.println("  resolved:             " + result.getResolved());
                System.out.println("  unresolved:           " + result.getUnresolved());
                System.out.println("  inserted:             " + result.getInserted());
                System.out.println("  updated:              " + result.getUpdated());
                System.out.println("  failed:               " + result.getFailed());
                System.out.println("  (source/assigner/dates/aggregation_state/granted_by_role are NULL: "
                        + "not provided by the current source)");
                printLines(result.getFailures());
                // CSS deletion detection: keep-set replicates BOTH of persistence's steps exactly —
                // the Optional identity filter (map(a) empty -> orElse(null) -> excluded, matching persist's
                // `if (mapped.isEmpty()) continue`) AND the id dedup (SoftDeleteSweeper.normalizeIds dedups,
                // matching persist's byId map). Same pure build over the full accounts/entitlements/identities
                // pulls; no partial-fetch (each pull throws on failure); empty-source guarded.
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_identity_entitlement", "id", assignments,
                        a -> com.keyforge.iiq.identityentitlement.IdentityEntitlementRowMapper.map(a)
                                .map(com.keyforge.iiq.identityentitlement.IdentityEntitlementRow::id).orElse(null));
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    // ---- Identity -> Role: kf_identity_role (authoritative: rest/identities/{id} assignedRoles[]) ----

    private static int runExtractIdentityRoles() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            List<IdentityRoleAssignment> rels =
                    new IdentityRoleService(new IiqApiClient(config)).getAllAssignments();
            long withDate = rels.stream().filter(a -> a.assignedDate() != null).count();
            long withAssigner = rels.stream().filter(a -> a.assigner() != null && !a.assigner().isBlank()).count();
            System.out.println();
            System.out.println("Extracted " + rels.size() + " identity-role assignments (rest/identities/{id} assignedRoles)"
                    + (rels.isEmpty() ? " - valid empty (no assignments on this instance)" : ""));
            System.out.println("  with assignment date: " + withDate + "  with assigner: " + withAssigner);
            int shown = Math.min(rels.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                IdentityRoleAssignment a = rels.get(i);
                System.out.println("  - identity=" + a.identityId() + " role=" + a.roleDisplayName()
                        + " (" + a.roleId() + ")");
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractIdentityRolesDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            List<IdentityRoleAssignment> rels =
                    new IdentityRoleService(new IiqApiClient(iiqConfig)).getAllAssignments();
            System.out.println("Extracted " + rels.size() + " identity-role assignments from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                IdentityRolePersistenceService svc = new IdentityRolePersistenceService(pgConfig.getSchema());
                IdentityRolePersistenceService.Result r = svc.persist(conn, rels);
                RunLedger.record("kf_identity_role", rels.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " identity-role edges to " + svc.targetTable()
                        + " (of " + rels.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                System.out.println("  (assigner/description are NULL: source exposes the fields but they are "
                        + "empty across live data)");
                printLines(r.getFailures());
                // CSS deletion detection: keep-set uses the SAME persisted rels list and the SAME PK
                // derivation persistence uses (IdentityRoleRowMapper.map(a).id()). getAllAssignments() is
                // fail-fast — a SCIM enumeration or any per-identity detail failure throws IiqApiException
                // and aborts BEFORE this point, so the sweep only ever runs on a COMPLETE source set.
                // An unmappable assignment throws IdentityRoleMappingException (a RuntimeException) which
                // sweepDeletionsByPk catches and excludes — exactly as persistOne skips it (never persisted
                // -> correctly absent from the keep-set). An empty valid list is protected by the
                // SoftDeleteSweeper empty-source guard (SKIP).
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_identity_role", "id",
                        rels, a -> com.keyforge.iiq.identityrole.IdentityRoleRowMapper.map(a).id());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Parquet workstream (Phase 1): direct IIQ -> Parquet (independent of PostgreSQL) ----
    // One independent extractor per dataset; extract-all-parquet just orchestrates them.

    /** Runs a single dataset's Parquet extractor (its own command). */
    private static int runOneParquet(String dataset) {
        return runParquet(List.of(dataset));
    }

    /** Orchestration only: invokes every individual Parquet extractor, each reporting independently. */
    private static int runAllParquet() {
        return runParquet(new ParquetExtractionService().allDatasetNames());
    }

    private static int runParquet(List<String> datasets) {
        try {
            AppConfig iiqConfig = AppConfig.load();
            ParquetConfig pq = ParquetConfig.load();
            // DuckDB extracts its native library to java.io.tmpdir; steer it to a dir on the same
            // (writable, ample) volume as the Parquet output so extraction never fails on a full temp drive.
            try {
                java.nio.file.Path duckTmp = pq.outputDir().toAbsolutePath().resolve(".duckdb-tmp");
                java.nio.file.Files.createDirectories(duckTmp);
                System.setProperty("java.io.tmpdir", duckTmp.toString());
            } catch (Exception ignore) {
                // fall back to the default temp dir
            }
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            ParquetExtractionService svc = new ParquetExtractionService();

            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("Parquet output: " + pq.outputDir().toAbsolutePath());
            System.out.println("Datasets: " + datasets);
            System.out.println();

            ParquetExtractionService.RunResult run = svc.extract(datasets, iiqConfig, pq, debugHttp);
            System.out.println("extraction_run_id: " + run.runId());
            int failedDatasets = 0;
            long totalWritten = 0;
            for (ParquetExtractionService.DatasetResult d : run.datasets()) {
                String status = d.error() != null
                        ? "ERROR: " + d.error()
                        : "extracted=" + d.extracted() + " written=" + d.written() + " failed=" + d.failed()
                          + (d.note() != null ? "  [source-limited: " + d.note() + "]" : "");
                System.out.println("  " + padRight(d.dataset(), 24) + status
                        + (d.file() != null ? "  -> " + d.file() : ""));
                if (d.error() != null) {
                    failedDatasets++;
                }
                totalWritten += d.written();
            }
            System.out.println();
            System.out.println("Total rows written: " + totalWritten + "  (datasets with errors: " + failedDatasets + ")");
            return failedDatasets > 0 ? 6 : 0;
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static String padRight(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    // ---- Phase 2: REST query service over the Parquet datasets (read-only; no IIQ, no PostgreSQL) ----

    private static void runStartRest() {
        try {
            ParquetConfig pq = ParquetConfig.load();
            RestConfig rest = RestConfig.load();
            // DuckDB extracts its native library to java.io.tmpdir; steer it to a writable volume.
            try {
                java.nio.file.Path duckTmp = pq.outputDir().toAbsolutePath().resolve(".duckdb-tmp");
                java.nio.file.Files.createDirectories(duckTmp);
                System.setProperty("java.io.tmpdir", duckTmp.toString());
            } catch (Exception ignore) {
                // fall back to the default temp dir
            }
            ParquetRestServer server = new ParquetRestServer(rest, pq);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
            Thread.currentThread().join(); // keep the process running while serving requests
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to start REST service: " + e.getMessage());
            System.exit(1);
        }
    }

    private static int runExtractWorkgroupMembers() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");

            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqSessionClient client = new IiqSessionClient(config, debugHttp);
            List<UserGroup> workgroups = WorkgroupPersistenceService.workgroupsOnly(
                    new UserGroupService(client).extractAll().getUserGroups());
            List<String> ids = workgroups.stream().map(UserGroup::getId).filter(s -> s != null).toList();
            System.out.println("Workgroups to inspect for membership: " + ids.size());

            WorkgroupMemberService.Result res = new WorkgroupMemberService(client).extractAll(ids);
            List<WorkgroupMembership> memberships = res.getMemberships();
            long distinctPairs = memberships.stream()
                    .map(m -> m.workgroupId() + "|" + m.identityId()).distinct().count();
            System.out.println();
            System.out.println("Membership rows: " + memberships.size()
                    + " (distinct workgroup/identity pairs: " + distinctPairs
                    + ", workgroups with 0 members: " + res.getWorkgroupsWithZeroMembers() + ")");
            for (String line : res.getStatus()) {
                System.out.println("  - " + line);
            }
            int shown = Math.min(memberships.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                WorkgroupMembership m = memberships.get(i);
                System.out.println("    member: " + m.memberName() + " (" + m.identityId()
                        + ") in workgroup " + m.workgroupId());
            }
            return res.anyFailed() ? 4 : 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractWorkgroupMembersDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");

            // One session serves both the Workgroup list and the per-workgroup member postbacks.
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            IiqSessionClient client = new IiqSessionClient(iiqConfig, debugHttp);
            List<UserGroup> workgroups = WorkgroupPersistenceService.workgroupsOnly(
                    new UserGroupService(client).extractAll().getUserGroups());
            List<String> ids = workgroups.stream().map(UserGroup::getId).filter(s -> s != null).toList();
            System.out.println("Workgroups to inspect for membership: " + ids.size());

            WorkgroupMemberService.Result res = new WorkgroupMemberService(client).extractAll(ids);
            List<WorkgroupMembership> memberships = res.getMemberships();
            System.out.println("Extracted " + memberships.size() + " membership rows across "
                    + res.getWorkgroupsProcessed() + " workgroups ("
                    + res.getWorkgroupsWithZeroMembers() + " with 0 members)");
            for (String line : res.getStatus()) {
                System.out.println("  - " + line);
            }

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                WorkgroupMemberPersistenceService svc =
                        new WorkgroupMemberPersistenceService(pgConfig.getSchema());
                WorkgroupMemberPersistenceService.Result result = svc.persist(conn, memberships);
                RunLedger.record("kf_workgroup_member", memberships.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " workgroup-member edges to " + svc.targetTable()
                        + " (of " + result.getDistinctEdges() + " distinct edges)");
                System.out.println("  input memberships:    " + result.getInput());
                System.out.println("  duplicates collapsed: " + result.getDuplicatesCollapsed());
                System.out.println("  distinct edges:       " + result.getDistinctEdges());
                System.out.println("  inserted:             " + result.getInserted());
                System.out.println("  updated:              " + result.getUpdated());
                System.out.println("  failed:               " + result.getFailed());
                printLines(result.getFailures());
                // CSS deletion detection — ONLY when every workgroup's membership fetch succeeded, so the
                // keep-set is authoritative (a partial fetch would wrongly delete un-fetched members).
                if (res.anyFailed()) {
                    System.out.println("  soft-delete sweep [kf_workgroup_member]: SKIPPED — membership fetch "
                            + "incomplete (a workgroup failed); avoiding unsafe deletions");
                } else {
                    sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_workgroup_member", "id", memberships,
                            m -> com.keyforge.iiq.workgroupmember.WorkgroupMemberRowMapper.map(m).id());
                }
                if (result.getFailed() > 0) {
                    return 6;
                }
                return res.anyFailed() ? 4 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static List<RoleEntitlementRow> buildRoleEntitlementRows(AppConfig iiqConfig, boolean debugHttp) {
        // Roles + entitlement catalog via Basic-auth SCIM (unchanged services).
        IiqApiClient basic = new IiqApiClient(iiqConfig);
        List<Role> roles = new RoleService(basic).getAllRoles();
        List<Entitlement> entitlements = new EntitlementService(basic).getAllEntitlements();
        Map<String, String> catalogIndex = RoleEntitlementRowMapper.buildCatalogIndex(entitlements);

        // Direct entitlements per role via the Role modeler UI endpoint (needs a web session).
        IiqSessionClient session = new IiqSessionClient(iiqConfig, debugHttp);
        RoleEntitlementService svc = new RoleEntitlementService(session);

        System.out.println("Roles: " + roles.size() + ", entitlement catalog: " + entitlements.size());
        List<RoleEntitlementRow> rows = new ArrayList<>();
        for (Role role : roles) {
            if (role.getId() == null) {
                continue;
            }
            for (RoleEntitlementGrant grant : svc.fetchGrantsFor(role.getId())) {
                rows.add(RoleEntitlementRowMapper.map(grant, role.getDisplayableName(), catalogIndex));
            }
        }
        return rows;
    }

    private static int runExtractRoleEntitlements() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));

            List<RoleEntitlementRow> rows = buildRoleEntitlementRows(config, debugHttp);
            long distinct = rows.stream().map(RoleEntitlementRow::id).distinct().count();
            long resolved = rows.stream().filter(r -> r.entitlementId() != null).count();
            System.out.println();
            System.out.println("Role→entitlement edges: " + rows.size()
                    + " (distinct keys: " + distinct + ", entitlement_id resolved: " + resolved
                    + ", unresolved: " + (rows.size() - resolved) + ")");
            int shown = Math.min(rows.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                RoleEntitlementRow r = rows.get(i);
                System.out.println("  - " + r.roleName() + " grants " + r.applicationName()
                        + ":" + r.property() + "=" + r.value() + " (entitlement_id=" + r.entitlementId() + ")");
            }
            System.out.println("(source: Role modeler direct entitlements; role→role hierarchy is NOT used)");
            return 0;

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runExtractRoleEntitlementsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl()
                    + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));

            List<RoleEntitlementRow> rows = buildRoleEntitlementRows(iiqConfig, debugHttp);
            System.out.println("Built " + rows.size() + " role→entitlement edges from the Role modeler source");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                RoleEntitlementPersistenceService svc =
                        new RoleEntitlementPersistenceService(pgConfig.getSchema());
                RoleEntitlementPersistenceService.Result result = svc.persist(conn, rows);
                RunLedger.record("kf_role_entitlement", rows.size(), result.getInserted(), result.getUpdated(), result.getFailed());
                System.out.println();
                System.out.println("Successfully persisted " + result.getPersisted()
                        + " role→entitlement edges to " + svc.targetTable()
                        + " (of " + result.getDistinctEdges() + " distinct edges)");
                System.out.println("  input edges:          " + result.getInput());
                System.out.println("  duplicates collapsed: " + result.getDuplicatesCollapsed());
                System.out.println("  distinct edges:       " + result.getDistinctEdges());
                System.out.println("  entitlement_id resolved:   " + result.getResolvedEntitlementIds());
                System.out.println("  entitlement_id unresolved: " + result.getUnresolvedEntitlementIds());
                System.out.println("  inserted:             " + result.getInserted());
                System.out.println("  updated:              " + result.getUpdated());
                System.out.println("  failed:               " + result.getFailed());
                printLines(result.getFailures());
                // CSS deletion detection: keep-set is the SAME already-built rows list passed to persist,
                // keyed by RoleEntitlementRow::id (a pure record accessor — no re-fetch, no re-map, no
                // throw). buildRoleEntitlementRows is fail-fast: a RoleService/EntitlementService full-pull
                // failure, any per-role modeler failure, or a malformed role id (mapper throw) all abort
                // BEFORE this point, so the sweep only ever runs on a COMPLETE source set. Persistence
                // dedups by row.id() and SoftDeleteSweeper.normalizeIds dedups the keep-set identically, so
                // the keep-set equals the persisted distinct id-set exactly. Empty rows -> empty-source SKIP.
                sweepDeletionsByPk(conn, pgConfig.getSchema(), "kf_role_entitlement", "id",
                        rows, com.keyforge.iiq.roleentitlement.RoleEntitlementRow::id);
                return result.getFailed() > 0 ? 6 : 0;
            }

        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (IiqApiException e) {
            System.err.println("IdentityIQ API error: " + e.getMessage());
            return 4;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    // ---- Phase 4a: Access Requests (kf_access_request, kf_request_item, kf_request_approval) ----

    private static int runExtractAccessRequests() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<AccessRequest> requests = new AccessRequestService(new IiqSessionClient(config, debugHttp)).getAllRequests();
            int items = requests.stream().mapToInt(r -> r.items() == null ? 0 : r.items().size()).sum();
            int approvals = requests.stream().mapToInt(r -> r.interactions() == null ? 0 : r.interactions().size()).sum();
            System.out.println();
            System.out.println("Extracted " + requests.size() + " access requests, " + items
                    + " request items, " + approvals + " approval interactions (ui/rest/identityRequests)");
            int shown = Math.min(requests.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                AccessRequest r = requests.get(i);
                System.out.println("  - req " + r.requestId() + " [" + r.type() + ", " + r.executionStatus()
                        + "] target=" + r.targetDisplayName() + " items=" + (r.items() == null ? 0 : r.items().size())
                        + " approvals=" + (r.interactions() == null ? 0 : r.interactions().size()));
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractAccessRequestsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<AccessRequest> requests = new AccessRequestService(com.keyforge.iiq.client.IiqClients.session(
                    iiqConfig, debugHttp, "sailpoint.object.IdentityRequest", "ui-rest")).getAllRequests();
            int items = requests.stream().mapToInt(r -> r.items() == null ? 0 : r.items().size()).sum();
            int approvals = requests.stream().mapToInt(r -> r.interactions() == null ? 0 : r.interactions().size()).sum();
            System.out.println("Extracted " + requests.size() + " access requests (" + items + " items, "
                    + approvals + " approvals) from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                AccessRequestPersistenceService svc = new AccessRequestPersistenceService(pgConfig.getSchema());
                AccessRequestPersistenceService.Result r = svc.persist(conn, requests);
                RunLedger.record("kf_access_request", requests.size(), r.getRequestsInserted(),
                        r.getRequests() - r.getRequestsInserted(), r.getFailed());
                RunLedger.record("kf_request_item", r.getItems(), r.getItemsInserted(),
                        r.getItems() - r.getItemsInserted(), 0);
                RunLedger.record("kf_request_approval", r.getApprovals(), r.getApprovalsInserted(),
                        r.getApprovals() - r.getApprovalsInserted(), 0);
                System.out.println();
                System.out.println("Persisted to " + svc.repository().requestTable() + " / "
                        + svc.repository().itemTable() + " / " + svc.repository().approvalTable());
                System.out.println("  kf_access_request:  " + r.getRequests() + " (inserted " + r.getRequestsInserted() + ")");
                System.out.println("  kf_request_item:    " + r.getItems() + " (inserted " + r.getItemsInserted() + ")");
                System.out.println("  kf_request_approval:" + r.getApprovals() + " (inserted " + r.getApprovalsInserted() + ")");
                System.out.println("  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Phase 5: Audit Events (kf_audit_event) ----

    private static int runExtractAuditEvents() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<AuditEvent> events = new AuditEventService(new IiqSessionClient(config, debugHttp)).getAllAuditEvents();
            System.out.println();
            System.out.println("Extracted " + events.size() + " audit events (analyze/audit/auditDataSource.json)");
            int shown = Math.min(events.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                AuditEvent e = events.get(i);
                System.out.println("  - " + e.action() + " | source=" + e.source()
                        + " | target=" + e.target() + " | created=" + e.created() + " | id=" + e.id());
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractAuditEventsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<AuditEvent> events = new AuditEventService(com.keyforge.iiq.client.IiqClients.session(
                    iiqConfig, debugHttp, "sailpoint.object.AuditEvent", "classic-ui")).getAllAuditEvents();
            System.out.println("Extracted " + events.size() + " audit events from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                AuditEventPersistenceService svc = new AuditEventPersistenceService(pgConfig.getSchema());
                AuditEventPersistenceService.Result r = svc.persist(conn, events);
                RunLedger.record("kf_audit_event", events.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted to " + svc.repository().table());
                System.out.println("  kf_audit_event:    " + r.getPersisted()
                        + " (inserted " + r.getInserted() + ", updated " + r.getUpdated() + ")");
                System.out.println("  duplicate ids skipped: " + r.getDuplicates());
                printLines(r.getDuplicateIds());
                System.out.println("  created timestamps unparsed (NULL created_at, raw kept): " + r.getTimestampUnparsed());
                System.out.println("  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Phase 4d (audit side): derived kf_event_link (audit event -> identity/account/entitlement) ----

    private static int runDeriveEventLinksDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Deriving kf_event_link from " + pgConfig.getSchema()
                    + ".kf_audit_event (resolving targets against usr / account / entitlement)");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                EventLinkPersistenceService svc = new EventLinkPersistenceService(pgConfig.getSchema());
                EventLinkPersistenceService.Result r = svc.persist(conn);
                RunLedger.record("kf_event_link", r.getExamined(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted to " + svc.repository().table());
                System.out.println("  audit events examined:       " + r.getExamined());
                System.out.println("  identity links resolved:     " + r.getResolvedIdentity());
                System.out.println("  account links resolved:      " + r.getResolvedAccount());
                System.out.println("  entitlement links resolved:  " + r.getResolvedEntitlement());
                System.out.println("  unresolved targets (kept):   " + r.getUnresolved());
                System.out.println("  ambiguous targets (no link): " + r.getAmbiguous());
                System.out.println("  out-of-scope typed (kept):   " + r.getOutOfScope());
                System.out.println("  no-target (null/blank, no row): " + r.getNoTarget());
                System.out.println("  rows persisted:              " + r.getPersisted()
                        + " (inserted " + r.getInserted() + ", updated " + r.getUpdated() + ")");
                System.out.println("  failed:                      " + r.getFailed());
                if (!r.getUnresolvedTargets().isEmpty()) {
                    System.out.println("  unresolved target values (preserved as dangling references):");
                    printLines(distinctSorted(r.getUnresolvedTargets()));
                }
                if (!r.getAmbiguousTargets().isEmpty()) {
                    System.out.println("  ambiguous target values:");
                    printLines(distinctSorted(r.getAmbiguousTargets()));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static List<String> distinctSorted(List<String> values) {
        return values.stream().distinct().sorted().toList();
    }

    // ---- Canonical reconciliation: additive PDF-canonical VIEWS over existing base tables ----

    private static int runReconcileCountsDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            com.keyforge.iiq.parquet.ParquetConfig pqConfig = com.keyforge.iiq.parquet.ParquetConfig.load();
            try {
                java.nio.file.Path duckTmp = pqConfig.outputDir().toAbsolutePath().resolve(".duckdb-tmp");
                java.nio.file.Files.createDirectories(duckTmp);
                System.setProperty("java.io.tmpdir", duckTmp.toString());
            } catch (Exception ignore) {
                // fall back to default temp dir
            }
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Parquet dir: " + pqConfig.outputDir().toAbsolutePath());
            System.out.println("Cross-pipeline count reconciliation (read-only over both stores; writes only kf_count_reconciliation)");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.countrecon.CountReconciliationRepository repo =
                        new com.keyforge.iiq.countrecon.CountReconciliationRepository(pgConfig.getSchema(), pqConfig);
                com.keyforge.iiq.countrecon.CountReconciliationService svc =
                        new com.keyforge.iiq.countrecon.CountReconciliationService(repo);
                String runId = RunLedger.currentRunId();
                if (runId == null) {
                    runId = java.util.UUID.randomUUID().toString();
                }
                com.keyforge.iiq.countrecon.CountReconciliationService.Result r = svc.reconcile(conn, runId);
                RunLedger.record("kf_count_reconciliation", r.domains().size(),
                        r.domains().size() - r.persistFailures(), 0, r.persistFailures());
                System.out.println();
                System.out.println("Domains: " + r.domains().size() + " (match " + r.matches()
                        + ", mismatch " + r.mismatches() + ", other " + r.other() + ")");
                for (com.keyforge.iiq.countrecon.CountReconciliationService.DomainResult d : r.domains()) {
                    String pg = d.pgCount() < 0 ? "absent" : Long.toString(d.pgCount());
                    String pq = d.parquetCount() < 0 ? "absent" : Long.toString(d.parquetCount());
                    System.out.println("  " + d.status() + "  " + d.pair().domain()
                            + " : pg=" + pg + " parquet=" + pq);
                }
                System.out.println();
                System.out.println("Written to " + repo.targetTable() + " (persist failures: " + r.persistFailures() + ")");
                return r.persistFailures() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runDeriveRecordLineageDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Deriving PDF lineage envelope into kf_record_lineage (read-only over domain tables; sidecar only)");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.lineage.RecordLineageService svc =
                        new com.keyforge.iiq.lineage.RecordLineageService(pgConfig.getSchema());
                com.keyforge.iiq.lineage.RecordLineageService.Result r = svc.deriveAll(conn);
                RunLedger.record("kf_record_lineage", r.totalRows(), r.totalRows(), 0, r.failedTables());
                System.out.println();
                System.out.println("Domain tables: " + r.tables().size()
                        + " (backfilled " + r.backfilledTables() + ", skipped " + r.skippedTables()
                        + ", failed " + r.failedTables() + ")");
                System.out.println("Lineage envelope rows written: " + r.totalRows());
                for (com.keyforge.iiq.lineage.RecordLineageService.TableResult t : r.tables()) {
                    if (com.keyforge.iiq.lineage.RecordLineageService.TableResult.BACKFILLED.equals(t.status())) {
                        System.out.println("  " + t.table() + ": " + t.rows() + " rows");
                    } else {
                        System.out.println("  [" + t.status() + "] " + t.table()
                                + (t.note() == null ? "" : " — " + t.note()));
                    }
                }
                System.out.println();
                System.out.println("Envelope written to " + svc.repository().targetTable()
                        + " (failed tables: " + r.failedTables() + ")");
                return r.failedTables() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runDeriveEventsDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Deriving append-only CEC events into kf_event (read-only over "
                    + "kf_request_approval/kf_task_result/kf_audit_event/kf_provisioning_txn and native "
                    + NativeSchemaConfig.resolve() + ".kf_workitem_archive; append-only, "
                    + "ON CONFLICT DO NOTHING)");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.event.EventPersistenceService svc =
                        new com.keyforge.iiq.event.EventPersistenceService(
                                pgConfig.getSchema(), NativeSchemaConfig.resolve());
                com.keyforge.iiq.event.EventPersistenceService.Result r = svc.persist(conn);
                // Append-only: 'updated' is always 0 (events are never mutated). Dedup is reported separately.
                RunLedger.record("kf_event", r.getDerived(), r.getAppended(), 0, r.getFailed());
                System.out.println();
                System.out.println("Derived " + r.getDerived() + " events; appended " + r.getAppended()
                        + " new, " + r.getDeduped() + " already present (deduped), " + r.getFailed() + " failed"
                        + " → " + svc.targetTable());
                for (Map.Entry<String, Integer> e : r.getDerivedByType().entrySet()) {
                    System.out.println("  " + e.getKey() + ": " + e.getValue());
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runDeriveNativeEventsDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("Native EVENT-zone derivation (append-only) from iiq_native source tables only — "
                    + "NO REST-derived data, NO AuditEvent. Sources: kf_identity_request_approval, kf_task_result, "
                    + "kf_provisioning_txn, kf_workitem_archive, kf_certification_item");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.nativeevent.NativeEventDerivationService svc =
                        new com.keyforge.iiq.nativeevent.NativeEventDerivationService(nativeSchema);
                String runId = RunLedger.currentRunId();
                if (runId == null) {
                    runId = java.util.UUID.randomUUID().toString();
                }
                com.keyforge.iiq.nativeevent.NativeEventDerivationService.Result r = svc.derive(conn, runId);
                RunLedger.record("kf_event", r.eventsDerived, r.eventsInserted,
                        r.eventsDerived - r.eventsInserted, 0);

                System.out.println();
                System.out.println("Source rows read (native): approval=" + r.approvalRows + ", task=" + r.taskRows
                        + ", provisioning=" + r.provRows + ", workitem_archive=" + r.archiveRows
                        + ", certification_item=" + r.certItemRows);
                if (!r.sourcesMissing.isEmpty()) {
                    System.out.println("  source tables absent (skipped): " + r.sourcesMissing);
                }
                System.out.println();
                System.out.println("Events derived: " + r.eventsDerived + " (inserted " + r.eventsInserted
                        + ", already-present " + (r.eventsDerived - r.eventsInserted) + ") -> " + svc.eventTable());
                for (Map.Entry<String, Integer> e : r.eventsByType.entrySet()) {
                    System.out.println("    " + e.getKey() + ": " + e.getValue());
                }
                System.out.println("Explicit links derived: " + r.linksDerived + " (inserted " + r.linksInserted
                        + ", already-present " + (r.linksDerived - r.linksInserted) + ") -> " + svc.linkTable());
                for (Map.Entry<String, Integer> e : r.linksByType.entrySet()) {
                    System.out.println("    " + e.getKey() + ": " + e.getValue());
                }
                System.out.println();
                System.out.println("All events stamped src_interface='native_iiq_java_api'; all links are EXPLICIT "
                        + "(from source id columns only, never inferred).");
                return 0;
            }
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runReconcileNativeWorkItemsDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            String nativeSchema = NativeSchemaConfig.resolve();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', native schema '" + nativeSchema + "')");
            System.out.println("WorkItem reconciliation (read-only over native tables; writes only kf_reconciliation_finding): "
                    + "approval work_item_id references vs live kf_workitem + kf_workitem_archive");

            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.reconciliation.WorkItemReconciliation svc =
                        new com.keyforge.iiq.reconciliation.WorkItemReconciliation(nativeSchema);
                String runId = RunLedger.currentRunId();
                if (runId == null) {
                    runId = java.util.UUID.randomUUID().toString();
                }
                com.keyforge.iiq.reconciliation.WorkItemReconciliation.Result r = svc.reconcile(conn, runId);
                RunLedger.record("kf_reconciliation_finding", 1, 1, 0, 0);

                System.out.println();
                if (r.skipped) {
                    System.out.println("Reconciliation SKIPPED: " + r.skipReason);
                    return 0;
                }
                System.out.println("Live WorkItems (kf_workitem):            " + r.liveWorkItems);
                System.out.println("WorkItem archives (kf_workitem_archive): " + r.workItemArchives);
                System.out.println("Approval work_item_id references (distinct): " + r.approvalRefs);
                System.out.println("  matched to a live WorkItem:      " + r.matchedLive);
                System.out.println("  matched to an archived WorkItem: " + r.matchedArchive);
                System.out.println("  matched to either:               " + r.matchedEither);
                System.out.println("  UNMATCHED:                       " + r.unmatched);
                if (!r.unmatchedSamples.isEmpty()) {
                    System.out.println("  unmatched sample ids: " + r.unmatchedSamples);
                }
                System.out.println();
                System.out.println("Determination:");
                System.out.println("  " + r.determination);
                System.out.println();
                System.out.println("Finding persisted to " + nativeSchema + ".kf_reconciliation_finding (check '"
                        + com.keyforge.iiq.reconciliation.WorkItemReconciliation.CHECK_NAME + "')");
                return 0;
            }
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 3;
        } catch (SQLException e) {
            System.err.println("PostgreSQL error: " + e.getMessage());
            return 5;
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static int runReconcileReferentialIntegrityDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Referential-integrity reconciliation (read-only over domain tables; writes only kf_reconciliation_finding)");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                com.keyforge.iiq.reconciliation.ReferentialIntegrityService svc =
                        new com.keyforge.iiq.reconciliation.ReferentialIntegrityService(pgConfig.getSchema());
                String runId = RunLedger.currentRunId();
                if (runId == null) {
                    runId = java.util.UUID.randomUUID().toString();
                }
                com.keyforge.iiq.reconciliation.ReferentialIntegrityService.Result r = svc.reconcile(conn, runId);
                RunLedger.record("kf_reconciliation_finding", r.total(),
                        r.total() - r.persistFailures(), 0, r.persistFailures());
                System.out.println();
                System.out.println("Checks: " + r.total() + " (checked " + r.checked()
                        + ", skipped " + r.skipped() + ")");
                System.out.println("Dangling references: " + r.totalOrphans()
                        + " across " + r.checksWithOrphans() + " check(s)");
                for (com.keyforge.iiq.reconciliation.ReconciliationFinding f : r.findings()) {
                    if (f.hasOrphans()) {
                        System.out.println("  [" + f.check().severity() + "] " + f.check().name()
                                + " : " + f.orphanCount() + " orphan(s); sample=" + f.sampleIds());
                    }
                }
                for (com.keyforge.iiq.reconciliation.ReconciliationFinding f : r.findings()) {
                    if (com.keyforge.iiq.reconciliation.ReconciliationFinding.SKIPPED.equals(f.status())) {
                        System.out.println("  [SKIPPED] " + f.check().name() + " : " + f.skipReason());
                    }
                }
                System.out.println();
                System.out.println("Findings persisted to " + svc.repository().targetTable()
                        + " (persist failures: " + r.persistFailures() + ")");
                return r.persistFailures() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runReconcileCanonicalViewsDb() {
        try {
            PgConfig pgConfig = PgConfig.load();
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            System.out.println("Maintaining derived kf_identity_account view; core canonical entities are physical tables");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                CanonicalViewReconciliationService svc =
                        new CanonicalViewReconciliationService(pgConfig.getSchema());
                CanonicalViewReconciliationService.Result r = svc.reconcile(conn);
                RunLedger.record("kf_identity_account", (int) Math.max(0, r.getIaTotal()), 0, 0, 0);
                System.out.println();
                System.out.println("core canonical entities (physical tables produced by extraction):");
                for (CanonicalViewReconciliationService.CoreReport c : r.getCore()) {
                    System.out.println("  " + svc.repository().qualified(c.name()) + ": " + c.state() + " — " + c.note());
                }
                System.out.println();
                System.out.println("kf_identity_account view: " + r.getLinkOutcome() + " — " + r.getLinkNote());
                boolean conflict = r.getLinkOutcome() == CanonicalViewReconciliationService.LinkOutcome.CONFLICT_SKIPPED;
                if (r.getIaTotal() >= 0) {
                    System.out.println("  total account edges:       " + r.getIaTotal());
                    System.out.println("  resolved:                  " + r.getIaResolved());
                    System.out.println("  unresolved (userid absent from identity table): " + r.getIaUnresolved());
                    System.out.println("  no identity reference:     " + r.getIaNoRef());
                }
                return conflict ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Domain 15: TaskResult (kf_task_result — extraction-provenance ledger) ----

    private static int runExtractTaskResults() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            List<TaskResult> results = new TaskResultService(new IiqApiClient(config)).getAllTaskResults();
            System.out.println();
            System.out.println("Extracted " + results.size() + " task results (SCIM /TaskResults)");
            int shown = Math.min(results.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                TaskResult t = results.get(i);
                System.out.println("  - " + t.name() + " [type=" + t.type() + ", status=" + t.completionStatus()
                        + ", launcher=" + t.launcher() + ", completed=" + t.completed() + "]");
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractTaskResultsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            List<TaskResult> results = new TaskResultService(
                    com.keyforge.iiq.client.IiqClients.basic(iiqConfig, "sailpoint.object.TaskResult", "scim"))
                    .getAllTaskResults();
            System.out.println("Extracted " + results.size() + " task results from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<TaskResult> plan = planExtraction(
                        conn, schema, "kf_task_result", "completed/launched", results, Main::taskResultChangeTime);
                TaskResultPersistenceService svc = new TaskResultPersistenceService(schema);
                TaskResultPersistenceService.Result r = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_task_result", plan.kept(), r.getInserted(), r.getUpdated(), r.getFailed());
                advanceWatermark(conn, schema, "kf_task_result", "completed/launched", plan, r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " task results to " + svc.targetTable()
                        + " (of " + plan.kept() + " selected, " + results.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                printLines(r.getFailures());

                // CSS deletion detection / soft-delete: mark rows no longer in the FULL source set as
                // deleted (never hard-deleted). `results` is always the full IIQ pull (even in
                // incremental mode), so the current source id set is authoritative here.
                java.util.List<String> currentIds = new java.util.ArrayList<>();
                for (TaskResult t : results) {
                    currentIds.add(com.keyforge.iiq.taskresult.TaskResultRowMapper.map(t).taskresultid());
                }
                com.keyforge.iiq.deletion.SoftDeleteSweeper.SweepResult sw =
                        new com.keyforge.iiq.deletion.SoftDeleteSweeper(schema)
                                .sweep(conn, "kf_task_result", "taskresultid", currentIds);
                if (sw.skipped()) {
                    System.out.println("  soft-delete sweep: SKIPPED — " + sw.note());
                } else {
                    System.out.println("  soft-delete sweep: current-source=" + sw.currentIdCount()
                            + "  marked deleted=" + sw.marked() + "  revived=" + sw.revived());
                }
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Certification campaigns: kf_certification_campaign (POST rest/certificationGroups) ----

    private static int runExtractCertifications() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<CertificationCampaign> campaigns =
                    new CertificationCampaignService(new IiqSessionClient(config, debugHttp)).getAllCampaigns();
            System.out.println();
            System.out.println("Extracted " + campaigns.size() + " certification campaigns (rest/certificationGroups)"
                    + (campaigns.isEmpty() ? " - valid empty (no campaigns on this instance)" : ""));
            int shown = Math.min(campaigns.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                CertificationCampaign c = campaigns.get(i);
                System.out.println("  - " + c.name() + " [status=" + c.status() + ", owner=" + c.ownerDisplayName()
                        + ", " + c.percentComplete() + "] id=" + c.id());
            }
            System.out.println("(campaign type/phase/start-end/sign-off and the item-decision hierarchy are NOT "
                    + "exposed by this endpoint -> stored NULL / out of scope; see report)");
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractCertificationsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<CertificationCampaign> campaigns =
                    new CertificationCampaignService(new IiqSessionClient(iiqConfig, debugHttp)).getAllCampaigns();
            System.out.println("Extracted " + campaigns.size() + " certification campaigns from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                CertificationCampaignPersistenceService svc =
                        new CertificationCampaignPersistenceService(pgConfig.getSchema());
                CertificationCampaignPersistenceService.Result r = svc.persist(conn, campaigns);
                RunLedger.record("kf_certification_campaign", campaigns.size(),
                        r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " certification campaigns to " + svc.targetTable()
                        + " (of " + campaigns.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Phase 4d (provisioning side): kf_event_link (ProvisioningTransaction -> Request / Certification) ----

    private static int runExtractProvisioningTransactions() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<ProvisioningTransaction> txns =
                    new ProvisioningTransactionService(new IiqSessionClient(config, debugHttp)).getAllTransactions();
            long withReq = txns.stream().filter(t -> t.accessRequestId() != null && !t.accessRequestId().isBlank()).count();
            long withCert = txns.stream().filter(t -> t.certificationName() != null && !t.certificationName().isBlank()).count();
            System.out.println();
            System.out.println("Extracted " + txns.size() + " provisioning transactions (rest/provisioningTransactions)");
            System.out.println("  carrying accessRequestId (-> Request):       " + withReq);
            System.out.println("  carrying certificationName (-> Certification): " + withCert);
            int shown = Math.min(txns.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                ProvisioningTransaction t = txns.get(i);
                System.out.println("  - txn " + t.name() + " [" + t.operation() + ", " + t.source() + ", " + t.status()
                        + "] identity=" + t.identityDisplayName() + " accessRequestId=" + t.accessRequestId()
                        + " certificationName=" + t.certificationName());
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractProvisioningTransactionsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<ProvisioningTransaction> txns =
                    new ProvisioningTransactionService(com.keyforge.iiq.client.IiqClients.session(
                            iiqConfig, debugHttp, "sailpoint.object.ProvisioningTransaction", "classic-rest"))
                            .getAllTransactions();
            System.out.println("Extracted " + txns.size() + " provisioning transactions from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ProvisioningTxnPersistenceService svc = new ProvisioningTxnPersistenceService(pgConfig.getSchema());
                ProvisioningTxnPersistenceService.Result r = svc.persist(conn, txns);
                RunLedger.record("kf_provisioning_txn", txns.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted to " + svc.repository().targetTable());
                System.out.println("  kf_provisioning_txn:  " + r.getPersisted()
                        + " (inserted " + r.getInserted() + ", updated " + r.getUpdated() + ")");
                System.out.println("  duplicate ids skipped: " + r.getDuplicates());
                printLines(r.getDuplicateIds());
                System.out.println("  created timestamps unparsed (NULL created_at, raw kept): " + r.getTimestampUnparsed());
                System.out.println("  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Provisioning items: kf_provisioning_item (GET rest/provisioningTransactions/{id} detail plan) ----

    private static int runExtractProvisioningItems() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<ProvisioningItem> items =
                    new ProvisioningItemService(new IiqSessionClient(config, debugHttp)).getAllItems();
            long attr = items.stream().filter(i -> "attribute".equals(i.requestType())).count();
            long perm = items.stream().filter(i -> "permission".equals(i.requestType())).count();
            long filt = items.stream().filter(i -> "filtered".equals(i.requestType())).count();
            System.out.println();
            System.out.println("Extracted " + items.size() + " provisioning items from transaction detail plans"
                    + " (attribute=" + attr + ", permission=" + perm + ", filtered=" + filt + ")");
            int shown = Math.min(items.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                ProvisioningItem it = items.get(i);
                System.out.println("  - [" + it.requestType() + "] " + it.operation() + " " + it.name()
                        + "=" + it.value() + " result=" + it.result() + " txn=" + it.parentTransactionId());
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractProvisioningItemsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<ProvisioningItem> items =
                    new ProvisioningItemService(new IiqSessionClient(iiqConfig, debugHttp)).getAllItems();
            System.out.println("Extracted " + items.size() + " provisioning items from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ProvisioningItemPersistenceService svc = new ProvisioningItemPersistenceService(pgConfig.getSchema());
                ProvisioningItemPersistenceService.Result r = svc.persist(conn, items);
                RunLedger.record("kf_provisioning_item", items.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " provisioning items to " + svc.targetTable()
                        + " (of " + items.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runDeriveProvisioningLinksDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<ProvisioningTransaction> txns =
                    new ProvisioningTransactionService(new IiqSessionClient(iiqConfig, debugHttp))
                            .getAllTransactionsWithReferences();
            System.out.println("Extracted " + txns.size()
                    + " provisioning transactions (with detail references) from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ProvisioningEventLinkPersistenceService svc =
                        new ProvisioningEventLinkPersistenceService(pgConfig.getSchema());
                ProvisioningEventLinkPersistenceService.Result r = svc.persist(conn, txns);
                RunLedger.record("kf_event_link", r.getExamined(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted to " + svc.repository().table() + " (source_object_type='ProvisioningTransaction')");
                System.out.println("  transactions examined:              " + r.getExamined());
                System.out.println("  transaction -> request resolved:    " + r.getRequestResolved());
                System.out.println("  transaction -> request unresolved:  " + r.getRequestUnresolved());
                System.out.println("  transaction -> request ambiguous:   " + r.getRequestAmbiguous());
                System.out.println("  transaction -> certification (kept, unresolved): " + r.getCertificationUnresolved());
                System.out.println("  transactions with no reference (no row):         " + r.getNoReference());
                System.out.println("  rows persisted:                     " + r.getPersisted()
                        + " (inserted " + r.getInserted() + ", updated " + r.getUpdated() + ")");
                System.out.println("  failed:                             " + r.getFailed());
                if (!r.getUnresolvedRequestRefs().isEmpty()) {
                    System.out.println("  unresolved accessRequestId values (preserved):");
                    printLines(distinctSorted(r.getUnresolvedRequestRefs()));
                }
                if (!r.getAmbiguousRequestRefs().isEmpty()) {
                    System.out.println("  ambiguous accessRequestId values:");
                    printLines(distinctSorted(r.getAmbiguousRequestRefs()));
                }
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    // ---- Phase 3: Governance configuration (kf_workflow_definition, kf_violation, kf_policy) ----

    private static int runExtractWorkflows() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            List<WorkflowDefinition> wfs = new WorkflowService(new IiqApiClient(config)).getAllWorkflows();
            System.out.println();
            System.out.println("Extracted " + wfs.size() + " workflow definitions (SCIM /Workflows)");
            int shown = Math.min(wfs.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                WorkflowDefinition w = wfs.get(i);
                System.out.println("  - " + w.name() + " [type=" + w.type() + ", handler=" + w.handler() + "]");
            }
            System.out.println("(approval scheme/mode/levels/escalation/e-signature are NULL: not exposed by SCIM "
                    + "- authoritative source is the workflow XML (JDBC/plugin), deferred)");
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractWorkflowsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            List<WorkflowDefinition> wfs = new WorkflowService(new IiqApiClient(iiqConfig)).getAllWorkflows();
            System.out.println("Extracted " + wfs.size() + " workflow definitions from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                String schema = pgConfig.getSchema();
                IncrementalFilter.Result<WorkflowDefinition> plan = planExtraction(
                        conn, schema, "kf_workflow_definition", "meta.lastModified", wfs, Main::workflowChangeTime);
                WorkflowPersistenceService svc = new WorkflowPersistenceService(schema);
                WorkflowPersistenceService.Result r = svc.persist(conn, plan.toPersist());
                RunLedger.record("kf_workflow_definition", plan.kept(), r.getInserted(), r.getUpdated(), r.getFailed());
                advanceWatermark(conn, schema, "kf_workflow_definition", "meta.lastModified", plan, r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " workflow definitions to " + svc.targetTable()
                        + " (of " + plan.kept() + " selected, " + wfs.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                System.out.println("  (approval-config columns are NULL: not exposed by SCIM - PARTIAL, XML source deferred)");
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractPolicyViolations() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            List<PolicyViolation> vs = new ViolationService(new IiqApiClient(config)).getAllViolations();
            System.out.println();
            System.out.println("Extracted " + vs.size() + " policy violations (SCIM /PolicyViolations)"
                    + (vs.isEmpty() ? " - valid empty (no violations on this instance)" : ""));
            int shown = Math.min(vs.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                PolicyViolation v = vs.get(i);
                System.out.println("  - policy=" + v.policyName() + " constraint=" + v.constraintName()
                        + " status=" + v.status()
                        + " identity=" + (v.identity() == null ? null : v.identity().displayName()));
            }
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractPolicyViolationsDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            List<PolicyViolation> vs = new ViolationService(new IiqApiClient(iiqConfig)).getAllViolations();
            System.out.println("Extracted " + vs.size() + " policy violations from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                ViolationPersistenceService svc = new ViolationPersistenceService(pgConfig.getSchema());
                ViolationPersistenceService.Result r = svc.persist(conn, vs);
                RunLedger.record("kf_violation", vs.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " policy violations to " + svc.targetTable()
                        + " (of " + vs.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractPolicies() {
        try {
            AppConfig config = AppConfig.load();
            System.out.println("Connecting to IdentityIQ at " + config.getBaseUrl()
                    + " as user '" + config.getUsername() + "'");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<PolicyDefinition> ps = new PolicyService(new IiqSessionClient(config, debugHttp)).getAllPolicies();
            System.out.println();
            System.out.println("Extracted " + ps.size() + " policies (define/policy/policiesDataSource.json)"
                    + (ps.isEmpty() ? " - valid empty (no policies on this instance)" : ""));
            int shown = Math.min(ps.size(), SAMPLE_SIZE);
            for (int i = 0; i < shown; i++) {
                PolicyDefinition p = ps.get(i);
                System.out.println("  - " + p.name() + " [type=" + p.type() + ", state=" + p.state() + "]");
            }
            System.out.println("(per-constraint detail is not in the list grid; deferred to the policy-editor/JDBC source)");
            return 0;
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static int runExtractPoliciesDb() {
        try {
            AppConfig iiqConfig = AppConfig.load();
            PgConfig pgConfig = PgConfig.load();
            System.out.println("IdentityIQ: " + iiqConfig.getBaseUrl() + " (user '" + iiqConfig.getUsername() + "')");
            System.out.println("PostgreSQL: " + pgConfig.getJdbcUrl()
                    + " (user '" + pgConfig.getUsername() + "', schema '" + pgConfig.getSchema() + "')");
            boolean debugHttp = !"false".equalsIgnoreCase(System.getenv("IIQ_DEBUG_HTTP"));
            List<PolicyDefinition> ps = new PolicyService(new IiqSessionClient(iiqConfig, debugHttp)).getAllPolicies();
            System.out.println("Extracted " + ps.size() + " policies from IdentityIQ");
            try (Connection conn = PostgresConnection.open(pgConfig)) {
                PolicyPersistenceService svc = new PolicyPersistenceService(pgConfig.getSchema());
                PolicyPersistenceService.Result r = svc.persist(conn, ps);
                RunLedger.record("kf_policy", ps.size(), r.getInserted(), r.getUpdated(), r.getFailed());
                System.out.println();
                System.out.println("Persisted " + r.getPersisted() + " policies to " + svc.targetTable()
                        + " (of " + ps.size() + " extracted)");
                System.out.println("  inserted: " + r.getInserted() + "  updated: " + r.getUpdated()
                        + "  failed: " + r.getFailed());
                printLines(r.getFailures());
                return r.getFailed() > 0 ? 6 : 0;
            }
        } catch (ConfigException e) { System.err.println("Configuration error: " + e.getMessage()); return 3;
        } catch (IiqApiException e) { System.err.println("IdentityIQ API error: " + e.getMessage()); return 4;
        } catch (SQLException e) { System.err.println("PostgreSQL error: " + e.getMessage()); return 5;
        } catch (RuntimeException e) { System.err.println("Unexpected error: " + e.getMessage()); return 1; }
    }

    private static void printUsage() {
        System.out.println("IdentityIQ Migration Tool");
        System.out.println();
        System.out.println("Usage: java -jar iiq-migration-tool.jar <command>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  extract-users         Retrieve all Identities from IdentityIQ and print a summary");
        System.out.println("  extract-users-db      Retrieve all Identities and upsert them into <schema>.kf_identity");
        System.out.println("  extract-applications  Retrieve all Applications from IdentityIQ and print a summary");
        System.out.println("  extract-applications-db  Retrieve all Applications and upsert them into <schema>.kf_application");
        System.out.println("  extract-applicationinstances-db  Retrieve all Applications and upsert them into <schema>.applicationinstance");
        System.out.println("  extract-entitlements  Retrieve all Entitlements from IdentityIQ and print a summary");
        System.out.println("  extract-entitlements-db  Retrieve all Entitlements and upsert them into <schema>.kf_entitlement");
        System.out.println("  extract-assignments-db   Derive Account->Entitlement assignments and upsert them into <schema>.entitlementassignment");
        System.out.println("  extract-catalog-db       Derive the requestable entitlement catalog and upsert it into <schema>.catalog");
        System.out.println("  extract-usergroups-db    Retrieve User Groups (Workgroups/Populations/Groups) from the IdentityIQ Group Configuration data sources and upsert into <schema>.usergroup");
        System.out.println("  extract-workitems-db     Retrieve Work Items from the IdentityIQ Work Items page (ui/rest/workItems/) and upsert into <schema>.workitem");
        System.out.println("  extract-roles         Retrieve all Roles (SCIM /Roles) from IdentityIQ and print a summary");
        System.out.println("  extract-roles-db      Retrieve all Roles (SCIM /Roles) and upsert them into <schema>.kf_role");
        System.out.println("  extract-role-hierarchy-db  Derive role->role edges (inherits/requires/permits) and upsert into <schema>.kf_role_hierarchy");
        System.out.println("  extract-role-entitlements    Summarise Role->Entitlement grants (Role modeler direct entitlements)");
        System.out.println("  extract-role-entitlements-db Retrieve Role->Entitlement grants and upsert into <schema>.kf_role_entitlement");
        System.out.println("  extract-workflows     Retrieve Workflow definitions (SCIM /Workflows) and print a summary");
        System.out.println("  extract-workflows-db  Retrieve Workflow definitions and upsert into <schema>.kf_workflow_definition (approval-config NULL/deferred)");
        System.out.println("  extract-policy-violations     Retrieve Policy Violations (SCIM /PolicyViolations) and print a summary");
        System.out.println("  extract-policy-violations-db  Retrieve Policy Violations and upsert into <schema>.kf_violation");
        System.out.println("  extract-policies      Retrieve Policy definitions (define/policy/policiesDataSource.json) and print a summary");
        System.out.println("  extract-policies-db   Retrieve Policy definitions and upsert into <schema>.kf_policy");
        System.out.println("  extract-access-requests     Retrieve Access Requests (ui/rest/identityRequests) and print a summary");
        System.out.println("  extract-access-requests-db  Retrieve Access Requests and upsert into <schema>.kf_access_request / kf_request_item / kf_request_approval");
        System.out.println("  extract-audit-events        Retrieve Audit Events (analyze/audit/auditDataSource.json) and print a summary");
        System.out.println("  extract-audit-events-db     Retrieve Audit Events and upsert into <schema>.kf_audit_event");
        System.out.println("  derive-event-links-db       Derive kf_event_link (audit event -> identity/account/entitlement) from <schema>.kf_audit_event");
        System.out.println("  derive-events-db            Derive append-only kf_event from request/task/audit/provisioning rows and native WorkItemArchive evidence (read-only, append-only)");
        System.out.println("  extract-provisioning-transactions  Retrieve Provisioning Transactions (rest/provisioningTransactions) and print a summary");
        System.out.println("  extract-provisioning-transactions-db  Retrieve Provisioning Transactions and upsert into <schema>.kf_provisioning_txn");
        System.out.println("  extract-provisioning-items          Retrieve provisioning items (transaction detail plans) and print a summary");
        System.out.println("  extract-provisioning-items-db       Retrieve provisioning items and upsert into <schema>.kf_provisioning_item");
        System.out.println("  extract-task-results       Retrieve Task Results (SCIM /TaskResults) and print a summary");
        System.out.println("  extract-task-results-db    Retrieve Task Results and upsert into <schema>.kf_task_result");
        System.out.println("  extract-certifications      Retrieve certification campaigns (rest/certificationGroups) and print a summary");
        System.out.println("  extract-certifications-db   Retrieve certification campaigns and upsert into <schema>.kf_certification_campaign");
        System.out.println("  derive-provisioning-links-db       Derive kf_event_link (provisioning txn -> request/certification) from rest/provisioningTransactions");
        System.out.println("  reconcile-canonical-views-db       Maintain the derived kf_identity_account view and report core canonical table state (kf_identity/kf_account/kf_application/kf_entitlement are physical tables)");
        System.out.println("  reconcile-referential-integrity-db Data-quality: detect dangling references across the normalized tables (read-only), writing findings to kf_reconciliation_finding");
        System.out.println("  reconcile-native-workitems-db      Reconcile native approval work_item_id references vs live kf_workitem + kf_workitem_archive (read-only; writes kf_reconciliation_finding)");
        System.out.println("  derive-native-events-db            Derive the native EVENT zone (append-only <iiq_native>.kf_event + kf_event_link) from native source tables only (no REST data, no AuditEvent)");
        System.out.println("  derive-record-lineage-db           Derive the PDF lineage envelope for every domain record into the shared kf_record_lineage sidecar (read-only over domain tables)");
        System.out.println("  reconcile-counts-db                Cross-pipeline count reconciliation: compare PostgreSQL vs Parquet row counts per domain (read-only), writing kf_count_reconciliation");
        System.out.println("  extract-workgroups    Retrieve Workgroups (Group Configuration DataSource, Workgroup subset) and print a summary");
        System.out.println("  extract-workgroups-db Retrieve Workgroups and upsert them into <schema>.kf_workgroup (usergroup extraction unchanged)");
        System.out.println("  extract-workgroup-members    Summarise Workgroup->Identity membership (Edit-Workgroup members grid)");
        System.out.println("  extract-workgroup-members-db Retrieve Workgroup->Identity membership and upsert into <schema>.kf_workgroup_member");
        System.out.println("  extract-object-owners Summarise verified object->owner relationships (Application/Role/Entitlement owners)");
        System.out.println("  extract-object-owners-db  Normalise verified owner relationships and upsert into <schema>.kf_object_owner");
        System.out.println("  extract-account-entitlements  Summarise the account->entitlement projection (reuses existing derivation)");
        System.out.println("  extract-account-entitlements-db  Project account->entitlement edges into <schema>.kf_account_entitlement");
        System.out.println("  extract-identity-entitlements    Summarise the identity->entitlement projection (reuses existing derivation)");
        System.out.println("  extract-identity-entitlements-db Project identity->entitlement edges into <schema>.kf_identity_entitlement (provenance NULL)");
        System.out.println("  extract-identity-roles       Retrieve authoritative Identity->Role assignments (rest/identities/{id}) and print a summary");
        System.out.println("  extract-identity-roles-db    Retrieve Identity->Role assignments and upsert into <schema>.kf_identity_role");
        System.out.println("  --- Native SailPoint Java-API workstream (via KeyForgeNativeIIQ plugin REST; separate IIQ_NATIVE_SCHEMA, default iiq_native) ---");
        System.out.println("  extract-native-identity-db   Pull native Identity data from the plugin endpoint and upsert into <iiq_native>.kf_identity (read-only in IIQ)");
        System.out.println("  extract-native-entitlement-db Pull native ManagedAttribute data from the plugin endpoint and upsert into <iiq_native>.kf_entitlement (read-only in IIQ)");
        System.out.println("  extract-native-application-db Pull native Application data from the plugin endpoint and upsert into <iiq_native>.kf_application (read-only in IIQ)");
        System.out.println("  extract-native-account-db    Pull native Link/account data from the plugin endpoint and upsert into <iiq_native>.kf_account (read-only in IIQ)");
        System.out.println("  extract-native-role-db       Pull native Role/Bundle data from the plugin endpoint and upsert into <iiq_native>.kf_role (read-only in IIQ)");
        System.out.println("  extract-native-role-relationships-db Pull native Bundle Profile/Permission/constraint edges and typed role hierarchy into <iiq_native>.kf_role_entitlement and kf_role_hierarchy");
        System.out.println("  extract-native-identity-role-db      Pull native Identity role assignments/detections into <iiq_native>.kf_identity_role (ASSIGNED/DETECTED edges, read-only in IIQ)");
        System.out.println("  extract-native-task-result-db        Pull native TaskResult run/aggregation history into <iiq_native>.kf_task_result (current-state, read-only in IIQ)");
        System.out.println("  extract-native-task-schedule-db      Pull native TaskSchedule cron schedules into <iiq_native>.kf_task_schedule (current-state, read-only in IIQ)");
        System.out.println("  extract-native-violations-db         Pull native PolicyViolation (identity/policy/constraint links) into <iiq_native>.kf_violation (current-state, read-only in IIQ)");
        System.out.println("  extract-native-policies-db           Pull native Policy definitions into <iiq_native>.kf_policy (current-state, read-only in IIQ)");
        System.out.println("  extract-native-audit-events-db       Append native AuditEvent historical log into <iiq_native>.kf_audit_event (append-only, read-only in IIQ)");
        System.out.println("  extract-native-policy-constraints-db Pull native Policy SoD/generic/activity constraints into <iiq_native>.kf_policy_constraint (FK policy_id, read-only in IIQ)");
        System.out.println("  extract-native-syslog-events-db      Append native SyslogEvent operational log into <iiq_native>.kf_syslog_event (append-only, read-only in IIQ)");
        System.out.println("  extract-native-parquet               Write the 10 approved native-source Parquet datasets from <iiq_native> PG, current-state + content-hash deduped (no versions)");
        System.out.println("  extract-native-workgroup-db  Pull native Workgroup data (Identity workgroup=true) from the plugin endpoint and upsert into <iiq_native>.kf_workgroup (read-only in IIQ)");
        System.out.println("  extract-native-group-definition-db  Pull native GroupDefinition data (Populations + Groups) from the plugin endpoint and upsert into <iiq_native>.kf_group_definition (read-only in IIQ)");
        System.out.println("  extract-native-workitem-archive-db  Append native WorkItemArchive evidence into <iiq_native>.kf_workitem_archive (read-only in IIQ; no deletion sweep)");
        System.out.println("  extract-native-workflows-db         Pull native Workflow definitions and static approval declarations into <iiq_native>.kf_workflow_definition");
        System.out.println("  extract-native-workgroup-memberships-db  Pull Identity.getWorkgroups() edges into <iiq_native>.kf_workgroup_member");
        System.out.println("  extract-native-identity-entitlement-db  Pull native IdentityEntitlement (identity↔entitlement + provenance) into <iiq_native>.kf_identity_entitlement (read-only in IIQ)");
        System.out.println("  extract-native-account-entitlement-db   Pull native account↔entitlement edges (Link entitlement attributes) into <iiq_native>.kf_account_entitlement (read-only in IIQ)");
        System.out.println("  extract-native-certification-db          Pull native Certification (campaign/cert) into <iiq_native>.kf_certification (read-only in IIQ)");
        System.out.println("  extract-native-certification-entity-db   Pull native CertificationEntity (who/what reviewed) into <iiq_native>.kf_certification_entity (read-only in IIQ)");
        System.out.println("  extract-native-certification-item-db     Pull native CertificationItem (reviewed item + decision/action) into <iiq_native>.kf_certification_item (read-only in IIQ)");
        System.out.println("  extract-native-certification-archive-db  Append native CertificationArchive (historical) into <iiq_native>.kf_certification_archive (read-only in IIQ; no deletion sweep)");
        System.out.println("  extract-native-provisioning-txn-db       Pull native ProvisioningTransaction (+ derived items) into <iiq_native>.kf_provisioning_txn + kf_provisioning_item (read-only in IIQ)");
        System.out.println("  extract-native-identity-request-db       Pull native IdentityRequest (+ items + request-time approvals) into <iiq_native>.kf_identity_request(_item/_approval) (read-only in IIQ)");
        System.out.println("  extract-native-workitem-db               Pull native live WorkItem (identityRequestId link + approval-set evidence) into <iiq_native>.kf_workitem (read-only in IIQ)");
        System.out.println("  --- Parquet workstream (direct IIQ->Parquet, independent of PostgreSQL; PARQUET_OUT_DIR default parquet-data) ---");
        System.out.println("  extract-task-results-parquet            Write task_result Parquet dataset");
        System.out.println("  extract-audit-events-parquet            Write kf_audit_event Parquet dataset");
        System.out.println("  extract-access-requests-parquet         Write kf_access_request Parquet dataset");
        System.out.println("  extract-request-items-parquet           Write kf_request_item Parquet dataset");
        System.out.println("  extract-request-approvals-parquet       Write kf_request_approval Parquet dataset");
        System.out.println("  extract-provisioning-transactions-parquet  Write kf_provisioning_txn Parquet dataset");
        System.out.println("  extract-provisioning-items-parquet      Write kf_provisioning_item Parquet dataset");
        System.out.println("  extract-violations-parquet              Write kf_violation Parquet dataset");
        System.out.println("  extract-cert-item-decisions-parquet     Write kf_cert_item_decision Parquet dataset (source-limited)");
        System.out.println("  extract-event-links-parquet             Write kf_event_link Parquet dataset (derived)");
        System.out.println("  extract-all-parquet                     Orchestrate: run every individual Parquet extractor");
        System.out.println("  start-rest                              Start the read-only REST query service over the Parquet datasets");
        System.out.println("                                          (DuckDB; no IIQ/PostgreSQL). Config: REST_HOST (default 127.0.0.1),");
        System.out.println("                                          REST_PORT (default 8100), PARQUET_OUT_DIR. GET /health, " + com.keyforge.iiq.rest.ParquetRestServer.PREFIX + "/datasets,");
        System.out.println("                                          " + com.keyforge.iiq.rest.ParquetRestServer.PREFIX + "/{dataset}[?fields=&sort=&order=&limit=&offset=&filter.<f>.<op>=]");
        System.out.println("                                          Native datasets (current.parquet): " + com.keyforge.iiq.rest.ParquetRestServer.NATIVE_PREFIX + "/datasets,");
        System.out.println("                                          " + com.keyforge.iiq.rest.ParquetRestServer.NATIVE_PREFIX + "/{dataset}[/schema][?...] (same query engine, isolated from " + com.keyforge.iiq.rest.ParquetRestServer.PREFIX + ")");
        System.out.println("  extract-accounts      Retrieve all Accounts from IdentityIQ and print a summary");
        System.out.println("  extract-accounts-db   Retrieve all Accounts and upsert them into <schema>.kf_account");
        System.out.println("  extract-assignments   Derive Account -> Entitlement assignments and print a summary");
        System.out.println("  help                  Show this help");
        System.out.println();
        System.out.println("Extraction mode (optional flag, applies to -db commands):");
        System.out.println("  --full           Process every extracted record (default; the initial/full load)");
        System.out.println("  --incremental    Persist only records changed at/after the stored per-entity");
        System.out.println("                   source watermark (kf_extraction_watermark). Supported for the");
        System.out.println("                   SCIM extractors that expose a real UTC change timestamp:");
        System.out.println("                   extract-users-db, extract-accounts-db, extract-applications-db,");
        System.out.println("                   extract-entitlements-db, extract-roles-db, extract-workflows-db,");
        System.out.println("                   extract-task-results-db. Other extractors ignore the flag and");
        System.out.println("                   always run full (their interfaces expose no reliable change field).");
        System.out.println("                   Example: java -jar iiq-migration-tool.jar extract-accounts-db --incremental");
        System.out.println();
        System.out.println("Incremental tuning (environment variable, optional):");
        System.out.println("  " + com.keyforge.iiq.incremental.IncrementalConfig.KEY_OVERLAP_MINUTES
                + "   Overlap window in minutes; incremental keeps records with source change-time >=");
        System.out.println("                              (watermark - overlap), absorbing clock skew / late commits "
                + "(default " + com.keyforge.iiq.incremental.IncrementalConfig.DEFAULT_OVERLAP_MINUTES + ").");
        System.out.println();
        System.out.println("Required configuration (environment variables):");
        System.out.println("  IIQ_BASE_URL     e.g. https://preview.keyforge.ai/identityiq/");
        System.out.println("  IIQ_USERNAME     IdentityIQ user for HTTP Basic auth");
        System.out.println("  IIQ_PASSWORD     password for that user");
        System.out.println();
        System.out.println("PostgreSQL configuration (environment variables) for the -db commands:");
        System.out.println("  PG_HOST          PostgreSQL host");
        System.out.println("  PG_PORT          PostgreSQL port (optional, default 5432)");
        System.out.println("  PG_DATABASE      database name");
        System.out.println("  PG_USERNAME      database user");
        System.out.println("  PG_PASSWORD      database password");
        System.out.println("  PG_SCHEMA        target schema for all migration tables "
                + "(optional, default " + com.keyforge.iiq.config.SchemaName.DEFAULT + ")");
    }
}
