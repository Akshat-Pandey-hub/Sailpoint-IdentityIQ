package com.keyforge.iiq.provisioningtransaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for provisioning-item extraction: parsing the real transaction <b>detail</b> plan
 * (attributeRequests / permissionRequests / filteredRequests), mapping, deterministic item id,
 * parent FK canonicalization, and NULL handling. Field shapes are taken verbatim from the live
 * {@code GET /rest/provisioningTransactions/{id}} responses.
 */
class ProvisioningItemTest {

    /** Real detail shape: 2 attributeRequests (incl. one with errorMessages), 1 filteredRequest. */
    private static final String DETAIL = "{\"id\":\"7f0001019f061fdc819fa50301271752\",\"name\":\"1\","
            + "\"operation\":\"Create\",\"attributeRequests\":["
            + "{\"operation\":\"Add\",\"name\":\"posixgroups\",\"value\":\"cn=qa,ou=rocktar,dc=moli,dc=org\","
            + "\"result\":\"failed\",\"reason\":null,\"attributeRequest\":false,\"errorMessages\":[]},"
            + "{\"operation\":\"Set\",\"name\":\"givenName\",\"value\":\"Emma\",\"result\":\"committed\","
            + "\"reason\":null,\"attributeRequest\":false,\"errorMessages\":[\"boom\"]}],"
            + "\"permissionRequests\":[],"
            + "\"filteredRequests\":[{\"operation\":\"Remove\",\"name\":\"posixgroups\",\"value\":\"devs\","
            + "\"result\":null,\"reason\":\"Does Not Exist\",\"attributeRequest\":true,\"errorMessages\":[]}]}";

    private final ProvisioningItemService service = new ProvisioningItemService(null);

    @Test
    void parsesAllThreePlanArraysWithParentAndIndex() {
        List<ProvisioningItem> items = service.parseItems(DETAIL);
        assertEquals(3, items.size()); // 2 attribute + 0 permission + 1 filtered

        ProvisioningItem a0 = items.get(0);
        assertEquals("7f0001019f061fdc819fa50301271752", a0.parentTransactionId());
        assertEquals("attribute", a0.requestType());
        assertEquals(0, a0.itemIndex());
        assertEquals("Add", a0.operation());
        assertEquals("posixgroups", a0.name());
        assertEquals("cn=qa,ou=rocktar,dc=moli,dc=org", a0.value());
        assertEquals("failed", a0.result());
        assertNull(a0.reason());
        assertEquals(Boolean.FALSE, a0.attributeRequest());
        assertEquals("[]", a0.errorMessagesJson());

        ProvisioningItem a1 = items.get(1);
        assertEquals(1, a1.itemIndex());
        assertEquals("committed", a1.result());
        assertTrue(a1.errorMessagesJson().contains("boom"));

        ProvisioningItem f = items.get(2);
        assertEquals("filtered", f.requestType());
        assertEquals(0, f.itemIndex());
        assertEquals("Remove", f.operation());
        assertNull(f.result());                 // filtered items carry a null result
        assertEquals("Does Not Exist", f.reason());
        assertEquals(Boolean.TRUE, f.attributeRequest());
    }

    @Test
    void mapsToRowWithCanonicalParentFkAndRawSource() {
        ProvisioningItem it = service.parseItems(DETAIL).get(0);
        ProvisioningItemRow row = ProvisioningItemRowMapper.map(it);

        // FK canonicalized identically to the parent transaction mapper.
        assertEquals(ProvisioningTxnRowMapper.toCanonicalUuid("7f0001019f061fdc819fa50301271752"), row.txnid());
        assertEquals("7f000101-9f06-1fdc-819f-a50301271752", row.txnid());
        assertEquals("7f0001019f061fdc819fa50301271752", row.sourceTxnId());
        assertEquals("attribute", row.requestType());
        assertEquals("Add", row.operation());
        assertEquals("[]", row.errorMessagesJson());
    }

    @Test
    void itemIdIsDeterministicAndDistinctPerItem() {
        List<ProvisioningItem> items = service.parseItems(DETAIL);
        String id0 = ProvisioningItemRowMapper.map(items.get(0)).itemid();
        String id1 = ProvisioningItemRowMapper.map(items.get(1)).itemid();
        String idFiltered = ProvisioningItemRowMapper.map(items.get(2)).itemid();

        // Stable across re-mapping (idempotent upsert relies on this).
        assertEquals(id0, ProvisioningItemRowMapper.map(items.get(0)).itemid());
        // Distinct per (type, index).
        assertNotEquals(id0, id1);
        assertNotEquals(id0, idFiltered);
        assertNotEquals(id1, idFiltered);
    }

    @Test
    void emptyPlanYieldsNoItems() {
        String empty = "{\"id\":\"7f0001019f061fdc819fa50301271752\",\"attributeRequests\":[],"
                + "\"permissionRequests\":[],\"filteredRequests\":[]}";
        assertTrue(service.parseItems(empty).isEmpty());
    }

    @Test
    void missingArraysYieldNoItems() {
        assertTrue(service.parseItems("{\"id\":\"7f0001019f061fdc819fa50301271752\"}").isEmpty());
    }

    @Test
    void repositoryRespectsPgSchema() {
        ProvisioningItemRepository repo = new ProvisioningItemRepository("iiq_migration_final");
        assertEquals("iiq_migration_final", repo.schema());
        assertEquals("iiq_migration_final.kf_provisioning_item", repo.targetTable());
    }
}
