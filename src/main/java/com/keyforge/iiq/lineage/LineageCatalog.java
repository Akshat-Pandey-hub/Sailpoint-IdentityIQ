package com.keyforge.iiq.lineage;

import java.util.List;

/**
 * The catalog of normalized domain tables and how to derive each one's PDF lineage envelope. Table,
 * PK and interface are taken from the actual repository DDLs (verified); {@code src_object_type} uses
 * the PDF's IIQ-class taxonomy. Optional columns (natural key / created / modified) are best-known
 * names that are existence-guarded at run time. This is the single place lineage mapping lives — no
 * lineage logic is duplicated across the ~28 extractors.
 */
public final class LineageCatalog {

    public static final String SRC_SYSTEM = "IdentityIQ";

    private LineageCatalog() {
    }

    public static List<LineageSource> all() {
        return List.of(
                // ---- core identity/access entities (SCIM) ----
                new LineageSource("usr", "userid", "sailpoint.object.Identity", "scim", "username", "created_at", "modified_at"),
                new LineageSource("application", "applicationid", "sailpoint.object.Application", "scim", "name", null, null),
                new LineageSource("applicationinstance", "instanceid", "sailpoint.object.Application", "derived", "instancename", null, null),
                new LineageSource("account", "accountid", "sailpoint.object.Link", "scim", null, null, null),
                new LineageSource("entitlement", "entitlementid", "sailpoint.object.ManagedAttribute", "scim", "value", "created_at", "modified_at"),
                new LineageSource("entitlementassignment", "assignmentid", "derived:AccountEntitlement", "derived", null, null, null),
                // Active account->entitlement projection (kf_account_entitlement). The legacy
                // 'entitlementassignment' above is absent in the live schema; this is the table that is
                // actually persisted/soft-deleted, so §6 lineage must cover it. Same derived semantics,
                // PK 'id', no source_id/natural/created/modified columns (all run-time guarded).
                new LineageSource("kf_account_entitlement", "id", "derived:AccountEntitlement", "derived", null, null, null),
                new LineageSource("catalog", "catalogid", "derived:EntitlementCatalog", "derived", "name", "created_at", "modified_at"),
                new LineageSource("usergroup", "id", "sailpoint.object.GroupDefinition", "classic-ui", "name", "created_at", "modified_at"),
                new LineageSource("workitem", "id", "sailpoint.object.WorkItem", "ui-rest", "name", "created_at", null),
                // ---- roles ----
                new LineageSource("kf_role", "roleid", "sailpoint.object.Bundle", "scim", "name", "created_at", "modified_at"),
                new LineageSource("kf_role_hierarchy", "hierarchyid", "derived:RoleHierarchy", "scim", null, null, null),
                new LineageSource("kf_role_entitlement", "id", "derived:RoleEntitlement", "classic-ui", null, null, null),
                new LineageSource("kf_identity_role", "id", "derived:IdentityRole", "classic-rest", null, null, null),
                new LineageSource("kf_identity_entitlement", "id", "derived:IdentityEntitlement", "derived", null, null, null),
                new LineageSource("kf_object_owner", "ownerid", "derived:ObjectOwner", "derived", null, null, null),
                // ---- workgroups ----
                new LineageSource("kf_workgroup", "workgroupid", "sailpoint.object.Identity", "classic-ui", "name", "created_at", "modified_at"),
                new LineageSource("kf_workgroup_member", "id", "derived:WorkgroupMember", "classic-ui", "member_name", null, null),
                // ---- access requests ----
                new LineageSource("kf_access_request", "requestid", "sailpoint.object.IdentityRequest", "ui-rest", "request_number", "created_at", null),
                new LineageSource("kf_request_item", "itemid", "sailpoint.object.IdentityRequestItem", "ui-rest", "request_number", null, null),
                new LineageSource("kf_request_approval", "id", "sailpoint.object.IdentityRequest.Approval", "ui-rest", "request_number", null, null),
                // ---- provisioning ----
                new LineageSource("kf_provisioning_txn", "txnid", "sailpoint.object.ProvisioningTransaction", "classic-rest", "name", "created_at", "modified_at"),
                new LineageSource("kf_provisioning_item", "itemid", "derived:ProvisioningTransactionItem", "classic-rest", "name", null, null),
                // ---- governance/audit ----
                new LineageSource("kf_audit_event", "auditid", "sailpoint.object.AuditEvent", "classic-ui", "action", "created_at", null),
                new LineageSource("kf_violation", "violationid", "sailpoint.object.PolicyViolation", "scim", "policy_name", null, null),
                new LineageSource("kf_policy", "policyid", "sailpoint.object.Policy", "classic-ui", "name", null, null),
                new LineageSource("kf_workflow_definition", "workflowid", "sailpoint.object.Workflow", "scim", "name", "created_at", "modified_at"),
                new LineageSource("kf_task_result", "taskresultid", "sailpoint.object.TaskResult", "scim", "name", null, null),
                new LineageSource("kf_event_link", "id", "derived:EventLink", "derived", null, null, null),
                new LineageSource("kf_certification_campaign", "campaignid", "sailpoint.object.CertificationGroup", "classic-rest", "name", "created_at", null));
    }
}
