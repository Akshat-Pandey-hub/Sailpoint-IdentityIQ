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

    private static NativeAccountEntitlementRecord perm(String link, String type, String target, String rights) {
        NativeAccountEntitlementRecord r = new NativeAccountEntitlementRecord();
        r.linkId = link;
        r.applicationName = "UnixApp";
        r.type = type;
        r.permissionTarget = target;
        r.permissionRights = rights;
        return r;
    }

    @Test
    void attributeEdgeIdFormulaIsUnchangedForTypeNullOrAttribute() {
        // type=null (legacy) and type=ATTRIBUTE must produce the SAME id as the original formula (stability).
        NativeAccountEntitlementRecord legacy = rec("l1", "memberOf", "g1"); // type == null
        NativeAccountEntitlementRecord typed = rec("l1", "memberOf", "g1");
        typed.type = "ATTRIBUTE";
        assertEquals(NativeAccountEntitlementRepository.canonicalEdgeId(legacy),
                NativeAccountEntitlementRepository.canonicalEdgeId(typed),
                "ATTRIBUTE edge id is stable across this change");
    }

    @Test
    void permissionEdgeIdIsDeterministicAndDistinctAcrossTypesAndTargets() {
        String p1 = NativeAccountEntitlementRepository.canonicalEdgeId(perm("l1", "PERMISSION", "/finance", "read"));
        String p1b = NativeAccountEntitlementRepository.canonicalEdgeId(perm("l1", "PERMISSION", "/finance", "read"));
        assertEquals(p1, p1b, "same permission edge ⇒ same id (never random)");
        assertEquals(p1, UUID.fromString(p1).toString());

        String p2 = NativeAccountEntitlementRepository.canonicalEdgeId(perm("l1", "PERMISSION", "/hr", "read"));
        assertNotEquals(p1, p2, "different target ⇒ different id");

        String tp = NativeAccountEntitlementRepository.canonicalEdgeId(perm("l1", "TARGET_PERMISSION", "/finance", "read"));
        assertNotEquals(p1, tp, "type disambiguates PERMISSION vs TARGET_PERMISSION on the same target");

        // a permission edge never collides with an attribute edge on the same link
        assertNotEquals(NativeAccountEntitlementRepository.canonicalEdgeId(rec("l1", "memberOf", "g1")), p1);
    }

    @Test
    void recordHashChangesWhenPermissionFieldsChange() {
        String base = NativeAccountEntitlementRepository.recordHash(perm("l1", "PERMISSION", "/finance", "read"));
        assertNotEquals(base, NativeAccountEntitlementRepository.recordHash(perm("l1", "PERMISSION", "/finance", "write")),
                "permission_rights participates in the hash");
        assertNotEquals(base, NativeAccountEntitlementRepository.recordHash(perm("l1", "PERMISSION", "/hr", "read")),
                "permission_target participates in the hash");
    }
}
