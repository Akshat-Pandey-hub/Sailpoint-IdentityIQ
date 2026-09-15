package com.keyforge.iiq.provisioningtransaction;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the kf_provisioning_txn mapping: canonical txnid, verbatim raw id preserved, transaction
 * fields passed through, the verified "M/d/yy, h:mm a" created parse (naive, minute precision) with
 * the raw string kept, off-pattern/blank display timestamps → NULL (never fabricated), and a missing
 * id reported rather than invented.
 */
class ProvisioningTxnRowMapperTest {

    private static final String RAW_ID = "7f0001019f061fdc819fa50301271752";
    private static final String CANON_ID = "7f000101-9f06-1fdc-819f-a50301271752";

    private static ProvisioningTransaction txn(String id, String created, String modified) {
        return new ProvisioningTransaction(id, "1", "Create", "LCM", "Failed",
                "Failed", "Auto", "Auto", "corp directory",
                "App_IDJ0001002", "Emma Coleman", "corp directory", null, null,
                created, modified, null, null,
                false, 0, false, true, true, null,
                null, null);
    }

    @Test
    void mapsCanonicalIdRawFieldsAndParsedCreated() {
        ProvisioningTxnRow r = ProvisioningTxnRowMapper.map(txn(RAW_ID, "7/28/26, 12:47 AM", null));
        assertEquals(CANON_ID, r.txnid());
        assertEquals(RAW_ID, r.sourceId());            // raw id preserved
        assertEquals("Create", r.operation());
        assertEquals("LCM", r.source());
        assertEquals("Failed", r.status());
        assertEquals("corp directory", r.integration());
        assertEquals(Boolean.TRUE, r.forced());
        assertEquals(Integer.valueOf(0), r.retryCount());
        assertEquals("7/28/26, 12:47 AM", r.createdDisplay());   // verbatim
        assertEquals(LocalDateTime.of(2026, 7, 28, 0, 47), r.createdAt());  // parsed, minute precision, no tz
    }

    @Test
    void offPatternOrBlankTimestampsBecomeNullNotFabricated() {
        assertNull(ProvisioningTxnRowMapper.parseDisplay("2026-07-28T00:47:00Z")); // ISO, not the display format
        assertNull(ProvisioningTxnRowMapper.parseDisplay(null));
        assertNull(ProvisioningTxnRowMapper.parseDisplay("   "));
        ProvisioningTxnRow r = ProvisioningTxnRowMapper.map(txn(RAW_ID, "7/28/26, 12:47 AM", null));
        assertNull(r.modifiedAt());   // source modified was null
        assertNull(r.lastRetry());
    }

    @Test
    void missingIdIsReportedNotInvented() {
        ProvisioningTxnMappingException ex = assertThrows(ProvisioningTxnMappingException.class,
                () -> ProvisioningTxnRowMapper.map(txn(null, "7/28/26, 12:47 AM", null)));
        assertEquals(true, ex.getMessage().toLowerCase().contains("id"));
    }
}
