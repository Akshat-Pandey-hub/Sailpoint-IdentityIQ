package com.keyforge.iiq.reconciliation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure, DB-free tests for the reconciliation check catalog and its SQL builders. */
class ReconciliationChecksTest {

    @Test
    void catalogIsWellFormed() {
        List<ReferentialCheck> checks = ReconciliationChecks.all();
        assertFalse(checks.isEmpty());
        Set<String> names = new HashSet<>();
        for (ReferentialCheck c : checks) {
            assertTrue(notBlank(c.childTable()), "childTable");
            assertTrue(notBlank(c.childColumn()), "childColumn");
            assertTrue(notBlank(c.parentTable()), "parentTable");
            assertTrue(notBlank(c.parentColumn()), "parentColumn");
            assertTrue(c.severity().equals("HIGH") || c.severity().equals("MEDIUM"), "severity");
            assertTrue(names.add(c.name()), "duplicate check name: " + c.name());
        }
    }

    @Test
    void catalogCoversKnownCoreRelationships() {
        Set<String> names = new HashSet<>();
        for (ReferentialCheck c : ReconciliationChecks.all()) {
            names.add(c.name());
        }
        // canonical physical tables/columns from the current persistence path (verified against DDLs)
        assertTrue(names.contains("kf_account_entitlement.account_id -> kf_account.accountid"));
        assertTrue(names.contains("kf_account_entitlement.entitlement_id -> kf_entitlement.entitlementid"));
        assertTrue(names.contains("kf_identity_entitlement.identity_id -> kf_identity.userid"));
        assertTrue(names.contains("kf_identity_role.roleid -> kf_role.roleid"));
        assertTrue(names.contains("kf_workgroup_member.identity_id -> kf_identity.userid"));
        assertTrue(names.contains("kf_request_item.requestid -> kf_access_request.requestid"));
        assertTrue(names.contains("kf_provisioning_item.txnid -> kf_provisioning_txn.txnid"));
        // no legacy table names remain in the canonical catalog
        assertFalse(names.stream().anyMatch(n -> n.contains("entitlementassignment")
                || n.contains(" usr.") || n.startsWith("usr.")
                || n.contains("-> account.") || n.contains("-> entitlement.") || n.contains("-> application.")));
    }

    @Test
    void countSqlIsSchemaQualifiedAntiJoin() {
        ReferentialCheck c = new ReferentialCheck("kf_account_entitlement", "account_id", "kf_account", "accountid", "HIGH");
        String sql = ReconciliationChecks.countSql("migration_test", c);
        assertTrue(sql.contains("FROM migration_test.kf_account_entitlement ch"));
        assertTrue(sql.contains("ch.account_id IS NOT NULL"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("migration_test.kf_account p"));
        assertTrue(sql.contains("p.accountid = ch.account_id"));
    }

    @Test
    void sampleSqlAppliesLimit() {
        ReferentialCheck c = new ReferentialCheck("kf_request_item", "requestid", "kf_access_request", "requestid", "HIGH");
        String sql = ReconciliationChecks.sampleSql("s", c, 5);
        assertTrue(sql.contains("::text"));
        assertTrue(sql.trim().endsWith("LIMIT 5"));
    }

    @Test
    void findingStatusHelpers() {
        ReferentialCheck c = new ReferentialCheck("a", "b", "c", "d", "MEDIUM");
        assertTrue(ReconciliationFinding.checked(c, 3, List.of("x")).hasOrphans());
        assertFalse(ReconciliationFinding.checked(c, 0, List.of()).hasOrphans());
        ReconciliationFinding s = ReconciliationFinding.skipped(c, "absent");
        assertEquals("SKIPPED", s.status());
        assertFalse(s.hasOrphans());
        assertEquals(-1, s.orphanCount());
    }

    @Test
    void findingIdIsDeterministicUuid() {
        // Root cause of the live persist bug: finding_id is a PostgreSQL uuid column, so the id must be
        // a real java.util.UUID (bound via setObject), not a varchar.
        UUID a = ReconciliationRepository.findingId("run1", "entitlementassignment.identity_id -> usr.userid");
        UUID b = ReconciliationRepository.findingId("run1", "entitlementassignment.identity_id -> usr.userid");
        assertEquals(a, b, "deterministic per (runId, checkName)");
        assertEquals(a, UUID.fromString(a.toString()), "valid UUID round-trip");
        assertNotEquals(a, ReconciliationRepository.findingId("run2", "entitlementassignment.identity_id -> usr.userid"));
        assertNotEquals(a, ReconciliationRepository.findingId("run1", "account.userid -> usr.userid"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
