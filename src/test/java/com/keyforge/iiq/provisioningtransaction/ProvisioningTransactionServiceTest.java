package com.keyforge.iiq.provisioningtransaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the real classic {@code rest/provisioningTransactions} envelope shape (root {@code objects},
 * total {@code count}), including the authoritative {@code accessRequestId}/{@code certificationName}
 * references and the all-null case observed live.
 */
class ProvisioningTransactionServiceTest {

    // Verbatim shape captured live: one transaction with no references (the live reality), and one
    // synthetic transaction carrying both references (to exercise the reference-bearing path).
    private static final String JSON =
            "{\"status\":\"success\",\"count\":2,\"objects\":["
            + "{\"id\":\"7f0001019f061fdc819fa50301271752\",\"name\":\"1\",\"operation\":\"Create\","
            + "\"source\":\"LCM\",\"status\":\"Failed\",\"identityName\":\"App_IDJ0001002\","
            + "\"identityDisplayName\":\"Emma Coleman\",\"applicationName\":\"corp directory\","
            + "\"created\":\"7/28/26, 12:47 AM\",\"accessRequestId\":null,\"certificationName\":null},"
            + "{\"id\":\"7f0001019f061fdc819fa50301271799\",\"name\":\"2\",\"operation\":\"Modify\","
            + "\"source\":\"LCM\",\"status\":\"Committed\",\"identityDisplayName\":\"Alice Martin\","
            + "\"accessRequestId\":\"0000000045\",\"certificationName\":\"Q3 Access Review\"}"
            + "]}";

    private static ProvisioningTransactionService svc() {
        return new ProvisioningTransactionService(null); // client unused by the pure parser
    }

    @Test
    void parsesObjectsEnvelopeAndTotal() {
        List<ProvisioningTransaction> txns = svc().parseTransactions(JSON);
        assertEquals(2, txns.size());
        assertEquals(2, svc().parseTotal(JSON));
    }

    @Test
    void preservesReferencesExactlyIncludingNulls() {
        List<ProvisioningTransaction> txns = svc().parseTransactions(JSON);
        ProvisioningTransaction noRef = txns.get(0);
        assertEquals("7f0001019f061fdc819fa50301271752", noRef.id());
        assertEquals("LCM", noRef.source());
        assertNull(noRef.accessRequestId());       // live reality: no reference
        assertNull(noRef.certificationName());

        ProvisioningTransaction withRef = txns.get(1);
        assertEquals("0000000045", withRef.accessRequestId());
        assertEquals("Q3 Access Review", withRef.certificationName());
    }

    @Test
    void emptyEnvelopeYieldsNoTransactions() {
        assertTrue(svc().parseTransactions("{\"status\":\"success\",\"count\":0,\"objects\":[]}").isEmpty());
        assertEquals(0, svc().parseTotal("{\"status\":\"success\",\"count\":0,\"objects\":[]}"));
    }
}
