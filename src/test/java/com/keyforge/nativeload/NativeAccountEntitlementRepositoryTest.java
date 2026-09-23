package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native account-entitlement edges. */
class NativeAccountEntitlementRepositoryTest {

    private static NativeAccountEntitlementRecord rec(String link, String attr, String value) {
        NativeAccountEntitlementRecord r = new NativeAccountEntitlementRecord();
        r.linkId = link;
        r.applicationName = "AD";
        r.nativeIdentity = "cn=alice";
        r.attributeName = attr;
        r.attributeValue = value;
        return r;
    }

    @Test
    void edgeIdIsDeterministicOverLinkAttrValue() {
        String a = NativeAccountEntitlementRepository.canonicalEdgeId(rec("l1", "memberOf", "g1"));
        String b = NativeAccountEntitlementRepository.canonicalEdgeId(rec("l1", "memberOf", "g1"));
        assertNotNull(a);
        assertEquals(a, b, "same edge ⇒ same id (never random)");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void differentValueYieldsDifferentEdgeId() {
        String g1 = NativeAccountEntitlementRepository.canonicalEdgeId(rec("l1", "memberOf", "g1"));
        String g2 = NativeAccountEntitlementRepository.canonicalEdgeId(rec("l1", "memberOf", "g2"));
        assertNotEquals(g1, g2);
    }

    @Test
    void recordHashChangesWhenValueChanges() {
        String h1 = NativeAccountEntitlementRepository.recordHash(rec("l1", "memberOf", "g1"));
        String h1b = NativeAccountEntitlementRepository.recordHash(rec("l1", "memberOf", "g1"));
        String h2 = NativeAccountEntitlementRepository.recordHash(rec("l1", "memberOf", "g2"));
        assertEquals(h1, h1b);
        assertNotEquals(h1, h2);
    }
}
