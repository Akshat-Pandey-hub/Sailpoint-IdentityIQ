package com.keyforge.iiq.accountentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Safety invariant for the kf_account_entitlement deletion sweep: its keep-set pk-function is
 * {@code AccountEntitlementRowMapper.map(a).id()} — the exact mapper persistence uses — so the keep-set
 * reproduces the persisted {@code id} for every assignment. {@code map} is unconditional (no Optional /
 * no filter), so no persisted edge can be omitted from the keep-set (which would be a false deletion).
 */
class AccountEntitlementDeletionKeyTest {

    private static AccountEntitlementAssignment a(String acct, String app, String attr, String val) {
        return new AccountEntitlementAssignment(acct, null, null, null, app, null, attr, val,
                null, null, null, null, null, null);
    }

    @Test
    void mapIdEqualsDeterministicFormula() {
        AccountEntitlementAssignment as = a("acct-1", "app-1", "groups", "cn=devs");
        assertEquals(AccountEntitlementRowMapper.deterministicId("acct-1", "app-1", "groups", "cn=devs"),
                AccountEntitlementRowMapper.map(as).id(),
                "keep-set id must equal the stored kf_account_entitlement.id");
    }

    @Test
    void idIsDeterministicAndFieldSensitive() {
        // same inputs -> same id (persist run and sweep run agree)
        assertEquals(AccountEntitlementRowMapper.map(a("acct-1", "app-1", "groups", "cn=devs")).id(),
                AccountEntitlementRowMapper.map(a("acct-1", "app-1", "groups", "cn=devs")).id());
        // a different entitlement value is a different edge
        assertNotEquals(AccountEntitlementRowMapper.map(a("acct-1", "app-1", "groups", "cn=devs")).id(),
                AccountEntitlementRowMapper.map(a("acct-1", "app-1", "groups", "cn=hr")).id());
    }

    @Test
    void mapIsUnconditional_everyAssignmentYieldsAKeepSetId() {
        // Unlike identity_entitlement's Optional projection, account_entitlement never drops a row,
        // so every persisted edge is in the keep-set -> no false deletion.
        assertNotNull(AccountEntitlementRowMapper.map(a(null, "app-1", "groups", "cn=devs")).id());
        assertNotNull(AccountEntitlementRowMapper.map(a("acct-1", null, null, null)).id());
    }
}
