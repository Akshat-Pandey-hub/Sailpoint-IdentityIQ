package com.keyforge.iiq.accountentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the account→entitlement projection maps the existing derivation faithfully:
 * ids canonicalised, unresolved values kept with a NULL entitlement_id, deterministic PK.
 * Nothing is re-derived or invented — it maps the model that already feeds
 * {@code entitlementassignment}.
 */
class AccountEntitlementRowMapperTest {

    private static AccountEntitlementAssignment resolved() {
        return new AccountEntitlementAssignment(
                "7f00010198421229819849ebc9ef0c71", "alexander.evans",
                "7f00010198421229819849ebc9ef0c72", "Alexander Evans",
                "7f00010198421229819849c815b90bfc", "EntraAuth",
                "groups", "GroupA-value",
                "7f0001019842122981984a0000000001", "Group A", "group",
                ResolutionStatus.RESOLVED, EnumSet.of(ResolutionSource.ACCOUNT_ATTRIBUTE), null);
    }

    @Test
    void mapsResolvedAssignment() {
        AccountEntitlementRow row = AccountEntitlementRowMapper.map(resolved());

        assertEquals("7f000101-9842-1229-8198-49ebc9ef0c71", row.accountId());
        assertEquals("alexander.evans", row.accountNativeName());
        assertEquals("7f000101-9842-1229-8198-49c815b90bfc", row.applicationId());
        assertEquals("EntraAuth", row.applicationName());
        assertEquals("7f000101-9842-1229-8198-4a0000000001", row.entitlementId());
        assertEquals("GroupA-value", row.entitlementValue());
        assertEquals("group", row.entitlementType());
        assertEquals("groups", row.sourceAttribute());
        assertEquals("RESOLVED", row.resolutionStatus());
        assertFalse(row.id().isBlank());
    }

    @Test
    void keepsUnresolvedWithNullEntitlementId() {
        AccountEntitlementAssignment unresolved = new AccountEntitlementAssignment(
                "7f00010198421229819849ebc9ef0c71", "alexander.evans", null, null,
                "7f00010198421229819849c815b90bfc", "EntraAuth",
                "groups", "OrphanValue",
                null, null, null,
                ResolutionStatus.UNRESOLVED, EnumSet.of(ResolutionSource.ACCOUNT_ATTRIBUTE), null);

        AccountEntitlementRow row = AccountEntitlementRowMapper.map(unresolved);
        assertNull(row.entitlementId());
        assertEquals("OrphanValue", row.entitlementValue());
        assertEquals("UNRESOLVED", row.resolutionStatus());
    }

    @Test
    void deterministicIdIsStableForSameEdge() {
        assertEquals(AccountEntitlementRowMapper.map(resolved()).id(),
                AccountEntitlementRowMapper.map(resolved()).id());
    }
}
