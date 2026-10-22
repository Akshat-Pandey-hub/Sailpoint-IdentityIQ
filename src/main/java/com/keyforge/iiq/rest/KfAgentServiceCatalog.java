package com.keyforge.iiq.rest;

import java.util.LinkedHashMap;
import java.util.Map;

/** Plain-language purpose of each KF Agent endpoint, printed on the debug SERVICE line. */
final class KfAgentServiceCatalog {

    private static final Map<String, String> PURPOSE = new LinkedHashMap<>();

    static {
        put("entitlements", "Entitlement Catalog: every grantable access item (ManagedAttribute) per application");
        put("identities", "Identities: every person/identity cube with attributes, manager, roles summary");
        put("applications", "Applications: every connected system with its configuration and schemas");
        put("accounts", "Accounts: every account (Link) on every application, with its attributes");
        put("workgroups", "Workgroups: every workgroup (team) with owner and capabilities");
        put("roles", "Roles: every role (Bundle) with type, owner and assignment rule");
        put("identity-roles", "Identity-Role: which identity holds which role (assigned or detected) and how");
        put("identity-entitlements", "Identity-Entitlement: each identity's access items with how they were granted");
        put("account-entitlements", "Account-Entitlement: entitlement values present on each account");
        put("role-entitlements", "Role-Entitlement: which entitlements each role grants (role profiles)");
        put("role-hierarchy", "Role hierarchy: inheritance / required / permitted links between roles");
        put("identity-requests", "Identity requests: access and lifecycle requests");
        put("identity-request-items", "Identity request items: each line of each request");
        put("identity-request-approvals", "Identity request approvals: approval evidence per request");
        put("policies", "Policies: every policy definition and its state");
        put("policy-constraints", "Policy constraints: the rules inside each policy");
        put("policy-violations", "Policy violations: current violations per identity");
        put("provisioning-transactions", "Provisioning transactions: each change IIQ pushed to an application");
        put("provisioning-items", "Provisioning items: each attribute/entitlement change inside a transaction");
        put("work-items", "Work items: open approvals and manual tasks");
        put("workitem-archives", "Work item archives: completed work items");
        put("workflow-definitions", "Workflow definitions: every business process");
        put("certifications", "Certifications: access review campaigns");
        put("certification-entities", "Certification entities: identities/objects under review");
        put("certification-items", "Certification items: each reviewed item and its decision");
        put("certification-archives", "Certification archives: archived reviews");
        put("audit-events", "Audit events: IdentityIQ's audit log");
        put("task-results", "Task results: results of tasks (aggregations, refreshes, workflows)");
        put("task-schedules", "Task schedules: scheduled tasks and their timing");
        put("access-history", "Access history: captured entitlement history");
        put("access-history/identity-events", "Access history: identity change events");
        put("access-history/role-events", "Access history: role change events");
        put("access-history/certifications", "Access history: historical certifications");
        put("group-definitions", "Group definitions: populations and group factories");
        put("workgroupmember", "Workgroup members: identity-to-workgroup links (Java API)");
        put("workgroup-members", "Workgroup members report: members per workgroup with status (SQL report)");
        put("entitlement-assignment", "Entitlement assignment report: why each identity has each entitlement (SQL report)");
        put("entitlement-certification", "Entitlement certification report: never-certified flag per entitlement (SQL report)");
        put("entitlement-certification-status", "Entitlement certification status report: last certification per entitlement (SQL report)");
        put("syslog-events", "Syslog events: IdentityIQ's internal error/warning log");
    }

    private KfAgentServiceCatalog() {
    }

    private static void put(String sub, String purpose) {
        PURPOSE.put(sub, purpose);
    }

    static String purpose(String sub) {
        String p = PURPOSE.get(sub);
        return p == null ? "unknown endpoint '" + sub + "'" : p;
    }
}
