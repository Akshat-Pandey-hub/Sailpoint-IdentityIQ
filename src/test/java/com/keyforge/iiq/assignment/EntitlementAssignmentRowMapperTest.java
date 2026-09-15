package com.keyforge.iiq.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the derived Entitlement Assignment → {@code entitlementassignment} mapping:
 * resolved account/entitlement FKs, deterministic stable id, and derivation provenance
 * stored in proper typed columns (not a JSON dump).
 */
class EntitlementAssignmentRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ACC_ID = "1feb5d42-037e-4c7d-ae15-b66d78f10429";
    private static final String ENT_ID_HEX = "7f00010198421229819849f9859c0e4a";
    private static final String ENT_ID_CANON = "7f000101-9842-1229-8198-49f9859c0e4a";
    private static final String IDENTITY_HEX = "7f00010198421229819849ebc9ef0c71";
    private static final String IDENTITY_CANON = "7f000101-9842-1229-8198-49ebc9ef0c71";
    private static final String APP_HEX = "7f00010198421229819849c815b90bfc";
    private static final String APP_CANON = "7f000101-9842-1229-8198-49c815b90bfc";

    private static AccountEntitlementAssignment assignment(String accountId, String applicationId,
                                                           String sourceAttribute, String entitlementValue,
                                                           String entitlementId) {
        return new AccountEntitlementAssignment(
                accountId, "179fcc53-native", IDENTITY_HEX, "Alexander Evans", applicationId, "EntraAuth",
                sourceAttribute, entitlementValue, entitlementId, "Domain Admins", "group",
                entitlementId != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED,
                EnumSet.of(ResolutionSource.ACCOUNT_ATTRIBUTE), MAPPER.createObjectNode());
    }

    @Test
    void resolvesFksAndMapsProvenanceToColumns() throws Exception {
        AccountEntitlementAssignment a = assignment(ACC_ID, APP_HEX, "groups", "G1", ENT_ID_HEX);
        EntitlementAssignmentRow row = EntitlementAssignmentRowMapper.map(a, Set.of(ACC_ID), Set.of(ENT_ID_CANON));

        assertEquals(ACC_ID, row.accountid());
        assertEquals(ENT_ID_CANON, row.entitlementid());
        assertEquals("179fcc53-native", row.accountNativeName());
        assertEquals(IDENTITY_CANON, row.identityId());
        assertEquals("Alexander Evans", row.identityDisplayName());
        assertEquals(APP_CANON, row.applicationId());
        assertEquals("EntraAuth", row.applicationName());
        assertEquals("G1", row.entitlementValue());
        assertEquals("group", row.entitlementType());
        assertEquals("groups", row.sourceAttribute());
        assertEquals("RESOLVED", row.resolutionStatus());

        JsonNode sources = MAPPER.readTree(row.resolutionSourcesJson());
        assertTrue(sources.isArray());
        assertEquals("ACCOUNT_ATTRIBUTE", sources.get(0).asText());
    }

    @Test
    void assignmentIdIsDeterministicAndStable() {
        AccountEntitlementAssignment a = assignment(ACC_ID, APP_HEX, "groups", "G1", ENT_ID_HEX);
        String first = EntitlementAssignmentRowMapper.map(a, Set.of(), Set.of()).assignmentid();
        String second = EntitlementAssignmentRowMapper.map(a, Set.of(), Set.of()).assignmentid();
        assertEquals(first, second);
    }

    @Test
    void distinctLogicalKeysProduceDistinctAssignmentIds() {
        String idA = EntitlementAssignmentRowMapper.deterministicAssignmentId(
                assignment(ACC_ID, APP_HEX, "groups", "G1", ENT_ID_HEX));
        String idB = EntitlementAssignmentRowMapper.deterministicAssignmentId(
                assignment(ACC_ID, APP_HEX, "roles", "G1", ENT_ID_HEX));
        String idC = EntitlementAssignmentRowMapper.deterministicAssignmentId(
                assignment(ACC_ID, APP_HEX, "groups", "G2", ENT_ID_HEX));
        assertNotEquals(idA, idB);
        assertNotEquals(idA, idC);
        assertNotEquals(idB, idC);
    }

    @Test
    void unresolvedEntitlementLeavesFkNullButKeepsProvenanceColumns() {
        AccountEntitlementAssignment a = assignment(ACC_ID, APP_HEX, "groups", "G-NATIVE", null);
        EntitlementAssignmentRow row = EntitlementAssignmentRowMapper.map(a, Set.of(ACC_ID), Set.of());

        assertNull(row.entitlementid());
        assertEquals("G-NATIVE", row.entitlementValue());       // provenance still captured
        assertEquals("UNRESOLVED", row.resolutionStatus());
    }

    @Test
    void unresolvedAccountLeavesFkNull() {
        AccountEntitlementAssignment a = assignment(ACC_ID, APP_HEX, "groups", "G1", ENT_ID_HEX);
        EntitlementAssignmentRow row = EntitlementAssignmentRowMapper.map(a, Set.of(), Set.of(ENT_ID_CANON));
        assertNull(row.accountid());
    }

    @Test
    void nonUuidProvenanceIdsBecomeNullNotFabricated() {
        // If identity/application ids aren't UUIDs, the id columns are NULL (never invented).
        AccountEntitlementAssignment a = new AccountEntitlementAssignment(
                ACC_ID, "native", "not-a-uuid", "Name", "also-not-uuid", "App",
                "groups", "G1", ENT_ID_HEX, "Disp", "group",
                ResolutionStatus.RESOLVED, EnumSet.of(ResolutionSource.ACCOUNT_ATTRIBUTE), MAPPER.createObjectNode());
        EntitlementAssignmentRow row = EntitlementAssignmentRowMapper.map(a, Set.of(ACC_ID), Set.of(ENT_ID_CANON));
        assertNull(row.identityId());
        assertNull(row.applicationId());
    }
}
