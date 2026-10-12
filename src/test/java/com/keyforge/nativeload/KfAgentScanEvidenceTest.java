package com.keyforge.nativeload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link KfAgentScanEvidence}: a REST service publishes the verified source count only when its internal full scan
 * passed the import's complete-scan guard; no count (or a mismatch, which throws) publishes nothing.
 */
class KfAgentScanEvidenceTest {

    private final NativeIdentityEntitlementRestService svc = new NativeIdentityEntitlementRestService();

    @BeforeEach
    @AfterEach
    void reset() {
        KfAgentScanEvidence.clear();
    }

    private static String row(String id) {
        return "{\"sourceId\":\"" + id + "\",\"identityId\":\"i1\",\"identityName\":\"A\",\"applicationName\":\"App\","
                + "\"attributeName\":\"groups\",\"attributeValue\":\"v-" + id + "\"}";
    }

    /** Single page then empty; {@code countJson} is the envelope's sourceCount member (or empty for none). */
    private static NativeIdentityEntitlementPageSource source(String countJson) {
        String page = "{\"entity\":\"IdentityEntitlement\"" + countJson + ",\"rows\":[" + row("e1") + "," + row("e2") + "]}";
        String empty = "{\"entity\":\"IdentityEntitlement\"" + countJson + ",\"rows\":[]}";
        return (start, limit) -> start == 0 ? page : empty;
    }

    @Test
    void recordsOnlyAMatchingNonNegativeCount() {
        KfAgentScanEvidence.verified(5, 5);
        assertEquals(5, KfAgentScanEvidence.current());
        KfAgentScanEvidence.clear();
        KfAgentScanEvidence.verified(-1, 3);
        assertNull(KfAgentScanEvidence.current(), "no count reported -> not verified");
        KfAgentScanEvidence.verified(5, 4);
        assertNull(KfAgentScanEvidence.current(), "mismatch -> not verified");
        KfAgentScanEvidence.verified(0, 0);
        assertEquals(0, KfAgentScanEvidence.current(), "a verified EMPTY population is still evidence");
        assertEquals("X-KF-Verified-Source-Count", KfAgentScanEvidence.HEADER);
    }

    @Test
    void aRestServiceWhoseGuardPassedPublishesTheVerifiedCount() {
        assertEquals(2, svc.fetch(source(",\"sourceCount\":2"), null, null, null).size());
        assertEquals(2, KfAgentScanEvidence.current());
    }

    @Test
    void aPluginWithoutASourceCountPublishesNothing() {
        assertEquals(2, svc.fetch(source(""), null, null, null).size());
        assertNull(KfAgentScanEvidence.current(), "the guard could not run, so the scan is not verified");
    }

    @Test
    void anIncompleteScanFailsAndPublishesNothing() {
        assertThrows(NativeImportException.class, () -> svc.fetch(source(",\"sourceCount\":3"), null, null, null));
        assertNull(KfAgentScanEvidence.current());
    }

    @Test
    void evidenceIsPerThread() throws Exception {
        KfAgentScanEvidence.verified(7, 7);
        Integer[] other = {-99};
        Thread t = new Thread(() -> other[0] = KfAgentScanEvidence.current());
        t.start();
        t.join();
        assertNull(other[0], "another request thread never sees this request's evidence");
        assertEquals(7, KfAgentScanEvidence.current());
    }
}
