package com.keyforge.nativeload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parent-scan endpoints (edges / children derived per parent object): {@link KfAgentScanEvidence} is published only
 * when the import's guard confirmed that EVERY parent was scanned (scanned parents == IIQ countObjects). A parent page
 * that under-reports or omits its parent count, or a missing IIQ total, never yields evidence. The published count is
 * the PARENT count, not the edge count.
 */
class KfAgentParentScanEvidenceTest {

    @BeforeEach
    @AfterEach
    void reset() {
        KfAgentScanEvidence.clear();
    }

    private static final String EDGE_1 = "{\"identityId\":\"i1\",\"identityName\":\"A\",\"roleId\":\"r1\","
            + "\"roleName\":\"R1\",\"relationshipType\":\"ASSIGNED\"}";
    private static final String EDGE_2 = "{\"identityId\":\"i2\",\"identityName\":\"B\",\"roleId\":\"r2\","
            + "\"roleName\":\"R2\",\"relationshipType\":\"DETECTED\"}";

    /** One page of identity-role edges then empty pages; {@code counts} = the envelope's count members. */
    private static NativeIdentityRolePageSource identityRoles(String counts) {
        String page = "{\"entity\":\"IdentityRole\"" + counts + ",\"rows\":[" + EDGE_1 + "," + EDGE_2 + "]}";
        String empty = "{\"entity\":\"IdentityRole\"" + counts.replaceAll("\"returnedIdentities\":\\d+",
                "\"returnedIdentities\":0") + ",\"rows\":[]}";
        return (start, limit) -> start == 0 ? page : empty;
    }

    @Test
    void everyIdentityScannedPublishesTheIdentityCountNotTheEdgeCount() {
        var svc = new NativeIdentityRoleRestService();
        assertEquals(2, svc.fetch(identityRoles(",\"sourceCount\":3,\"returnedIdentities\":3"), null, null, null).size());
        assertEquals(3, KfAgentScanEvidence.current(), "3 identities scanned (one has no roles) — 2 edges");
    }

    @Test
    void aParentPageThatUnderReportsFailsTheScan() {
        var svc = new NativeIdentityRoleRestService();
        assertThrows(NativeImportException.class,
                () -> svc.fetch(identityRoles(",\"sourceCount\":3,\"returnedIdentities\":2"), null, null, null));
        assertNull(KfAgentScanEvidence.current(), "a missed parent can never look complete");
    }

    @Test
    void aMissingParentCountFailsTheScan() {
        var svc = new NativeIdentityRoleRestService();
        assertThrows(NativeImportException.class,
                () -> svc.fetch(identityRoles(",\"sourceCount\":3"), null, null, null));
        assertNull(KfAgentScanEvidence.current(), "no returnedIdentities -> 0 scanned != 3");
    }

    @Test
    void noIiqTotalMeansNoEvidence() {
        var svc = new NativeIdentityRoleRestService();
        assertEquals(2, svc.fetch(identityRoles(",\"returnedIdentities\":2"), null, null, null).size());
        assertNull(KfAgentScanEvidence.current(), "the guard cannot run without countObjects");
    }

    private static NativeRoleRelationshipPageSource bundles(int sourceCount, int returned) {
        return (start, limit, runId) -> "{\"sourceCount\":" + sourceCount + ",\"returnedBundles\":"
                + (start == 0 ? returned : 0) + ",\"extractionRunId\":\"" + runId
                + "\",\"roleEntitlements\":[],\"roleHierarchy\":[]}";
    }

    @Test
    void roleRelationshipsPublishTheBundleCountOnlyWhenEveryBundleWasScanned() {
        new NativeRoleEntitlementRestService().fetch(bundles(4, 4), null, null, null);
        assertEquals(4, KfAgentScanEvidence.current());
        KfAgentScanEvidence.clear();

        new NativeRoleHierarchyRestService().fetch(bundles(4, 4), null, null, null);
        assertEquals(4, KfAgentScanEvidence.current());
        KfAgentScanEvidence.clear();

        assertThrows(NativeImportException.class,
                () -> new NativeRoleEntitlementRestService().fetch(bundles(4, 3), null, null, null));
        assertNull(KfAgentScanEvidence.current());
    }
}
