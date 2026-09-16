package com.keyforge.iiq.reconciliation;

import java.util.List;

/**
 * The declarative catalog of referential-integrity checks over the existing normalized schema, plus
 * the pure SQL builders used to execute them. Table/column names are the real ones from the project's
 * repository DDLs (e.g. {@code entitlementassignment.identity_id -> usr.userid}); no relationship is
 * inferred. Every check is a guarded anti-join so an absent table/column degrades to SKIPPED rather
 * than erroring or producing a false result.
 */
public final class ReconciliationChecks {

    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";

    private ReconciliationChecks() {
    }

    /**
     * The full check catalog, referencing the actual canonical physical tables produced by the current
     * persistence path: {@code kf_identity}(userid), {@code kf_account}(accountid), {@code kf_application}
     * (applicationid), {@code kf_entitlement}(entitlementid), and the account→entitlement edge
     * {@code kf_account_entitlement}(account_id/application_id/entitlement_id). The identity→entitlement
     * relationship is covered by the {@code kf_identity_entitlement} checks (that edge has an identity
     * column; {@code kf_account_entitlement} does not). Order is stable (report reads top-to-bottom).
     */
    public static List<ReferentialCheck> all() {
        return List.of(
                // account → entitlement edge (canonical kf_account_entitlement)
                new ReferentialCheck("kf_account_entitlement", "account_id", "kf_account", "accountid", HIGH),
                new ReferentialCheck("kf_account_entitlement", "entitlement_id", "kf_entitlement", "entitlementid", HIGH),
                new ReferentialCheck("kf_account_entitlement", "application_id", "kf_application", "applicationid", MEDIUM),
                // account core edges
                new ReferentialCheck("kf_account", "userid", "kf_identity", "userid", MEDIUM),
                new ReferentialCheck("kf_account", "instanceid", "applicationinstance", "instanceid", MEDIUM),
                new ReferentialCheck("applicationinstance", "applicationid", "kf_application", "applicationid", MEDIUM),
                // requestable catalog
                new ReferentialCheck("catalog", "entitlementid", "kf_entitlement", "entitlementid", MEDIUM),
                new ReferentialCheck("catalog", "appinstanceid", "applicationinstance", "instanceid", MEDIUM),
                // identity relationships
                new ReferentialCheck("kf_identity_entitlement", "identity_id", "kf_identity", "userid", HIGH),
                new ReferentialCheck("kf_identity_entitlement", "entitlement_id", "kf_entitlement", "entitlementid", MEDIUM),
                new ReferentialCheck("kf_identity_role", "identityid", "kf_identity", "userid", HIGH),
                new ReferentialCheck("kf_identity_role", "roleid", "kf_role", "roleid", HIGH),
                // role graph
                new ReferentialCheck("kf_role_entitlement", "role_id", "kf_role", "roleid", MEDIUM),
                new ReferentialCheck("kf_role_entitlement", "entitlement_id", "kf_entitlement", "entitlementid", MEDIUM),
                new ReferentialCheck("kf_role_hierarchy", "role_id", "kf_role", "roleid", MEDIUM),
                new ReferentialCheck("kf_role_hierarchy", "related_role_id", "kf_role", "roleid", MEDIUM),
                // workgroups
                new ReferentialCheck("kf_workgroup_member", "workgroup_id", "kf_workgroup", "workgroupid", HIGH),
                new ReferentialCheck("kf_workgroup_member", "identity_id", "kf_identity", "userid", HIGH),
                // access request graph
                new ReferentialCheck("kf_request_item", "requestid", "kf_access_request", "requestid", HIGH),
                new ReferentialCheck("kf_request_approval", "requestid", "kf_access_request", "requestid", HIGH),
                // provisioning
                new ReferentialCheck("kf_provisioning_item", "txnid", "kf_provisioning_txn", "txnid", HIGH));
    }

    /**
     * Counts child rows with a non-null reference that has no matching parent. Identifiers come from
     * the fixed catalog (never caller input) and the schema is validated upstream, so inlining them is
     * safe; there are no bound value parameters in this statement.
     */
    public static String countSql(String schema, ReferentialCheck c) {
        String child = schema + "." + c.childTable();
        String parent = schema + "." + c.parentTable();
        return "SELECT count(*) FROM " + child + " ch "
                + "WHERE ch." + c.childColumn() + " IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM " + parent + " p WHERE p." + c.parentColumn()
                + " = ch." + c.childColumn() + ")";
    }

    /** Up to {@code limit} distinct offending reference values (as text) for evidence. */
    public static String sampleSql(String schema, ReferentialCheck c, int limit) {
        String child = schema + "." + c.childTable();
        String parent = schema + "." + c.parentTable();
        return "SELECT DISTINCT ch." + c.childColumn() + "::text AS v FROM " + child + " ch "
                + "WHERE ch." + c.childColumn() + " IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM " + parent + " p WHERE p." + c.parentColumn()
                + " = ch." + c.childColumn() + ") LIMIT " + limit;
    }
}
