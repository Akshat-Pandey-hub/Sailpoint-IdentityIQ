package com.keyforge.iiq.eventlink;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the deterministic, conservative target resolution against name→id maps modelled on the
 * live instance: identity display names, the typed {@code Identity:App_IDJ...} form (resolved via
 * usr.username), the two-{@code Joe Baker} ambiguity, out-of-scope typed targets, and system
 * strings that must stay unresolved rather than fabricate a link.
 */
class EventLinkResolverTest {

    // usr: displayname 'Alice Martin' -> EMP00001; username 'App_IDJ0001008' -> Maya Foster id;
    // two 'Joe Baker' identities (ambiguous).
    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String MAYA = "22222222-2222-2222-2222-222222222222";
    private static final String JOE_A = "33333333-3333-3333-3333-333333333333";
    private static final String JOE_B = "44444444-4444-4444-4444-444444444444";
    private static final String ACCT = "55555555-5555-5555-5555-555555555555";
    private static final String ENT = "66666666-6666-6666-6666-666666666666";

    private static EventLinkResolver resolver() {
        Map<String, Set<String>> identities = Map.of(
                "Alice Martin", Set.of(ALICE),
                "App_IDJ0001008", Set.of(MAYA),
                "Joe Baker", Set.of(JOE_A, JOE_B));
        Map<String, Set<String>> accounts = Map.of("App_IDJ0001009", Set.of(ACCT));
        Map<String, Set<String>> entitlements = Map.of("IT Operations Employee", Set.of(ENT));
        return new EventLinkResolver(identities, accounts, entitlements);
    }

    @Test
    void untypedDisplayNameResolvesToIdentity() {
        EventLinkResolver.Result r = resolver().resolve("Alice Martin");
        assertEquals(EventLinkResolver.Status.RESOLVED, r.status());
        assertEquals("Identity", r.targetType());
        assertEquals(ALICE, r.targetId());
        assertNull(r.typeHint());
    }

    @Test
    void typedIdentityResolvesViaUsername() {
        EventLinkResolver.Result r = resolver().resolve("Identity:App_IDJ0001008");
        assertEquals(EventLinkResolver.Status.RESOLVED, r.status());
        assertEquals("Identity", r.targetType());
        assertEquals(MAYA, r.targetId());
        assertEquals("Identity", r.typeHint());
    }

    @Test
    void ambiguousDisplayNameIsNotLinked() {
        EventLinkResolver.Result r = resolver().resolve("Joe Baker");
        assertEquals(EventLinkResolver.Status.AMBIGUOUS, r.status());
        assertNull(r.targetId());
        assertNull(r.targetType());
    }

    @Test
    void systemStringStaysUnresolved() {
        EventLinkResolver.Result r = resolver().resolve("ServerDown");
        assertEquals(EventLinkResolver.Status.UNRESOLVED, r.status());
        assertNull(r.targetId());
    }

    @Test
    void outOfScopeTypedTargetIsFlaggedNotLinked() {
        EventLinkResolver.Result r = resolver().resolve("Application: corp directory-LDAP-Target");
        assertEquals(EventLinkResolver.Status.OUT_OF_SCOPE_TYPE, r.status());
        assertEquals("Application", r.typeHint());
        assertNull(r.targetId());
    }

    @Test
    void typedAccountAndEntitlementResolveAgainstTheirOwnTables() {
        EventLinkResolver.Result acct = resolver().resolve("Account:App_IDJ0001009");
        assertEquals(EventLinkResolver.Status.RESOLVED, acct.status());
        assertEquals("Account", acct.targetType());
        assertEquals(ACCT, acct.targetId());

        EventLinkResolver.Result ent = resolver().resolve("Entitlement:IT Operations Employee");
        assertEquals(EventLinkResolver.Status.RESOLVED, ent.status());
        assertEquals("Entitlement", ent.targetType());
        assertEquals(ENT, ent.targetId());
    }

    @Test
    void untypedNeverCrossMatchesAccountOrEntitlement() {
        // 'App_IDJ0001009' exists as an account name but NOT as an identity; untyped resolves
        // Identity-only, so it must NOT be linked to the account.
        EventLinkResolver.Result r = resolver().resolve("App_IDJ0001009");
        assertEquals(EventLinkResolver.Status.UNRESOLVED, r.status());
        assertNull(r.targetId());
    }
}
