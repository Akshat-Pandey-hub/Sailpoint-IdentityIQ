package com.keyforge.iiq.provisioningtransaction;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the provisioning-side link derivation: Request resolution against
 * {@code kf_access_request} (by request_number or requestid, exactly-one), the ambiguity guard,
 * unresolved/preserved references, the always-unresolved certification link, deterministic PKs, and
 * that the transaction's raw id/references are preserved. Uses synthetic transactions that carry
 * references, since the live instance has none.
 */
class ProvisioningEventLinkTest {

    private static final String REQ_UUID = "7f000101-9fbf-1a17-819f-cd9791db1c96";
    private static final String TXN_RAW = "7f0001019f061fdc819fa50301271799";
    private static final String TXN_CANON = "7f000101-9f06-1fdc-819f-a50301271799";

    // kf_access_request keyed by request_number "0000000045" and by its requestid.
    private static ProvisioningLinkResolver resolver() {
        return new ProvisioningLinkResolver(Map.of(
                "0000000045", Set.of(REQ_UUID),
                REQ_UUID, Set.of(REQ_UUID),
                "0000000099", Set.of("id-a", "id-b"))); // ambiguous
    }

    private static ProvisioningTransaction txn(String accessRequestId, String certificationName) {
        return new ProvisioningTransaction(TXN_RAW, "2", "Modify", "LCM", "Committed",
                "Committed", "Auto", "Auto", "corp directory",
                "App_IDJ0001009", "Alice Martin", "corp directory", "App_IDJ0001009", "Alice Martin",
                "7/28/26, 12:47 AM", null, null, null,
                false, 0, false, true, true, null,
                accessRequestId, certificationName);
    }

    @Test
    void requestReferenceResolvesToAccessRequest() {
        ProvisioningLinkResolver.Result r = resolver().resolveRequest("0000000045");
        assertEquals(ProvisioningLinkResolver.Status.RESOLVED, r.status());
        assertEquals("AccessRequest", r.targetType());
        assertEquals(REQ_UUID, r.targetId());

        ProvisioningEventLinkRow row = ProvisioningEventLinkRowMapper.mapRequestLink(txn("0000000045", null), r);
        assertEquals(TXN_CANON, row.sourceObjectId());
        assertEquals("ProvisioningTransaction", row.sourceObjectType());
        assertEquals("AccessRequest", row.targetObjectType());
        assertEquals(REQ_UUID, row.targetObjectId());
        assertEquals("0000000045", row.targetRaw());   // raw reference preserved
        assertEquals("RESOLVED", row.linkStatus());
    }

    @Test
    void unknownRequestReferenceIsUnresolvedAndPreserved() {
        ProvisioningLinkResolver.Result r = resolver().resolveRequest("0000000404");
        assertEquals(ProvisioningLinkResolver.Status.UNRESOLVED, r.status());
        ProvisioningEventLinkRow row = ProvisioningEventLinkRowMapper.mapRequestLink(txn("0000000404", null), r);
        assertEquals("UNRESOLVED", row.linkStatus());
        assertNull(row.targetObjectId());
        assertEquals("0000000404", row.targetRaw());   // dangling reference preserved
    }

    @Test
    void ambiguousRequestReferenceIsNotLinked() {
        ProvisioningLinkResolver.Result r = resolver().resolveRequest("0000000099");
        assertEquals(ProvisioningLinkResolver.Status.AMBIGUOUS, r.status());
        assertNull(r.targetId());
    }

    @Test
    void certificationLinkIsAlwaysUnresolvedWithRawNamePreserved() {
        ProvisioningEventLinkRow row = ProvisioningEventLinkRowMapper.mapCertificationLink(txn(null, "Q3 Access Review"));
        assertEquals("UNRESOLVED", row.linkStatus());
        assertEquals("Certification", row.targetTypeHint());
        assertEquals("Q3 Access Review", row.targetRaw());   // name preserved, no fabricated decision id
        assertNull(row.targetObjectId());
    }

    @Test
    void requestAndCertificationLinksHaveDistinctDeterministicPks() {
        ProvisioningLinkResolver.Result r = resolver().resolveRequest("0000000045");
        ProvisioningEventLinkRow req = ProvisioningEventLinkRowMapper.mapRequestLink(txn("0000000045", "C"), r);
        ProvisioningEventLinkRow cert = ProvisioningEventLinkRowMapper.mapCertificationLink(txn("0000000045", "C"));
        assertNotEquals(req.id(), cert.id());
        // deterministic / stable
        assertEquals(req.id(), ProvisioningEventLinkRowMapper.mapRequestLink(txn("0000000045", "C"), r).id());
    }

    @Test
    void missingTransactionIdIsReportedNotInvented() {
        ProvisioningTransaction bad = new ProvisioningTransaction(null, "x", "Create", "LCM", "Failed",
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                "0000000045", null);
        assertThrows(ProvisioningEventLinkMappingException.class,
                () -> ProvisioningEventLinkRowMapper.mapRequestLink(bad,
                        new ProvisioningLinkResolver.Result(ProvisioningLinkResolver.Status.RESOLVED, "AccessRequest", REQ_UUID, "x")));
    }
}
