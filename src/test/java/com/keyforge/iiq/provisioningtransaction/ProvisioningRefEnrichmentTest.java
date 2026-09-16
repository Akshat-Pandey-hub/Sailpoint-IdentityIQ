package com.keyforge.iiq.provisioningtransaction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the event-link enrichment: the provisioning-transaction LIST leaves accessRequestId /
 * certificationName null, so the derivation must read them from the per-transaction DETAIL route (the
 * same source the Parquet event-link dataset uses). Tests the pure enrichment with a lambda fetcher
 * (no live client).
 */
class ProvisioningRefEnrichmentTest {

    private static ProvisioningTransaction txn(String id, String name) {
        return new ProvisioningTransaction(id, name, "op", "src", "status", "sm", "type", "tm", "integ",
                "idN", "idDN", "app", "nat", "acct", "created", "modified", "lr", "tkt", Boolean.TRUE, 3,
                Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, "result", null, null);
    }

    @Test
    void enrichesReferencesFromDetailNotList() {
        List<ProvisioningTransaction> list = List.of(txn("t1", "n1"), txn("t2", "n2"), txn("t3", "n3"));
        Map<String, String> detail = Map.of(
                "t1", "{\"id\":\"t1\",\"accessRequestId\":\"0000000021\",\"certificationName\":null}",
                "t2", "{\"id\":\"t2\",\"accessRequestId\":null,\"certificationName\":\"Kf_Test Target Cert\"}",
                "t3", "{\"id\":\"t3\",\"accessRequestId\":null,\"certificationName\":null}");

        List<ProvisioningTransaction> out = ProvisioningTransactionService.enrichReferences(
                list, id -> detail.getOrDefault(id, "{}"));

        assertEquals(3, out.size());
        assertEquals("0000000021", out.get(0).accessRequestId());
        assertNull(out.get(0).certificationName());
        assertNull(out.get(1).accessRequestId());
        assertEquals("Kf_Test Target Cert", out.get(1).certificationName());
        assertNull(out.get(2).accessRequestId());
        assertNull(out.get(2).certificationName());
        // core fields survive the copy
        assertEquals("t1", out.get(0).id());
        assertEquals("n2", out.get(1).name());
    }

    @Test
    void detailFailureKeepsListLevelNullReferences() {
        List<ProvisioningTransaction> list = List.of(txn("t1", "n1"));
        List<ProvisioningTransaction> out = ProvisioningTransactionService.enrichReferences(
                list, id -> { throw new RuntimeException("detail 500"); });
        assertEquals(1, out.size());
        assertNull(out.get(0).accessRequestId());
        assertNull(out.get(0).certificationName());
        assertEquals("t1", out.get(0).id()); // not dropped
    }

    @Test
    void withReferencesCopiesAllFieldsAndOverridesRefs() {
        ProvisioningTransaction c = txn("id", "name").withReferences("AR1", "CERT1");
        assertEquals("id", c.id());
        assertEquals("app", c.applicationName());
        assertEquals("result", c.result());
        assertEquals(Integer.valueOf(3), c.retryCount());
        assertEquals(Boolean.TRUE, c.forced());
        assertEquals("AR1", c.accessRequestId());
        assertEquals("CERT1", c.certificationName());
    }
}
