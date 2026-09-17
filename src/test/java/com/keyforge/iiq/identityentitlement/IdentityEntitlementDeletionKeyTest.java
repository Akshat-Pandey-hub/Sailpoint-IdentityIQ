package com.keyforge.iiq.identityentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety invariant for the kf_identity_entitlement deletion sweep.
 *
 * <p>Unlike kf_account_entitlement, {@code IdentityEntitlementRowMapper.map} is an Optional projection:
 * it drops any assignment that has no correlated identity ({@code canonicalOrNull(identityId) == null}
 * &rarr; {@link java.util.Optional#empty()}). Persistence applies exactly this filter
 * ({@code if (mapped.isEmpty()) continue}) plus an id dedup ({@code byId.putIfAbsent}).
 *
 * <p>The sweep's keep-set pk-function is the SAME expression the persist path resolves to:
 * {@code map(a).map(IdentityEntitlementRow::id).orElse(null)}. A no-identity assignment therefore
 * yields {@code null} (excluded from the keep-set) — but it was never persisted either, so this is
 * NOT a false deletion. Every assignment that IS persisted yields its exact stored {@code id} in the
 * keep-set, and {@code SoftDeleteSweeper.normalizeIds} reproduces the byId dedup. So the keep-set
 * equals the persisted id-set exactly.
 */
class IdentityEntitlementDeletionKeyTest {

    // a valid identity id -> canonicalOrNull != null -> map present
    private static final String IDENTITY = "7f00010198421229819849f9859c0e4a";

    private static AccountEntitlementAssignment a(String identityId, String app, String attr, String val) {
        // ctor: (accountId, accountName, identityId, identityDisplayName, applicationId, applicationName,
        //        sourceAttribute, entitlementValue, entitlementId, entitlementDisplayName, entitlementType,
        //        resolutionStatus, resolutionSources, customAttributes)
        return new AccountEntitlementAssignment("acct-1", null, identityId, null, app, null, attr, val,
                null, null, null, null, null, null);
    }

    /** The exact pk-function the sweep uses (see runExtractIdentityEntitlementsDb). */
    private static String keepSetPk(AccountEntitlementAssignment as) {
        return IdentityEntitlementRowMapper.map(as).map(IdentityEntitlementRow::id).orElse(null);
    }

    @Test
    void keepSetPkEqualsPersistedIdForAnEdgeWithIdentity() {
        AccountEntitlementAssignment as = a(IDENTITY, "app-1", "groups", "cn=devs");
        String persistedId = IdentityEntitlementRowMapper.map(as).orElseThrow().id();
        assertEquals(persistedId, keepSetPk(as), "keep-set pk must equal the persisted kf_identity_entitlement.id");
        // and it is exactly deterministicId(rawIdentityId, applicationId, sourceAttribute, value)
        assertEquals(IdentityEntitlementRowMapper.deterministicId(IDENTITY, "app-1", "groups", "cn=devs"),
                persistedId);
    }

    @Test
    void noIdentityAssignmentIsExcludedFromKeepSetAndWasNeverPersisted() {
        AccountEntitlementAssignment noId = a(null, "app-1", "groups", "cn=devs");
        // persistence drops it: map(a).isEmpty() -> `continue` -> never a persisted row
        assertTrue(IdentityEntitlementRowMapper.map(noId).isEmpty());
        // the sweep pk-function returns null -> excluded from the keep-set (matches the persist filter),
        // so it can never be falsely marked deleted (it isn't in the table to begin with).
        assertNull(keepSetPk(noId));
    }

    @Test
    void keepSetPkIsDeterministicAndFieldSensitive() {
        // persist run and later sweep run agree
        assertEquals(keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")),
                keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")));
        // a different entitlement value is a different edge
        assertNotEquals(keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")),
                keepSetPk(a(IDENTITY, "app-1", "groups", "cn=hr")));
        // a different application is a different edge
        assertNotEquals(keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")),
                keepSetPk(a(IDENTITY, "app-2", "groups", "cn=devs")));
        assertNotNull(keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")));
    }

    @Test
    void twoAssignmentsForTheSameEdgeCollapseToOneKeepSetPk() {
        // persistence dedups via byId.putIfAbsent(row.id(), ...); the keep-set dedups via
        // SoftDeleteSweeper.normalizeIds. Both key on this identical id.
        assertEquals(keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")),
                keepSetPk(a(IDENTITY, "app-1", "groups", "cn=devs")));
    }
}
