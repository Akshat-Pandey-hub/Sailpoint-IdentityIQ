package com.keyforge.iiq.identityentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the identity→entitlement projection: maps the identity edge from the existing
 * derivation, keeps authoritative provenance NULL (never fabricated), skips assignments with
 * no identity, and collapses duplicate edges (same identity+app+attr+value via two accounts).
 */
class IdentityEntitlementRowMapperTest {

    private static AccountEntitlementAssignment assignment(String accountId, String identityId) {
        return new AccountEntitlementAssignment(
                accountId, "acct-native", identityId, "Alexander Evans",
                "7f00010198421229819849c815b90bfc", "EntraAuth",
                "groups", "GroupA-value",
                "7f0001019842122981984a0000000001", "Group A", "group",
                ResolutionStatus.RESOLVED, EnumSet.of(ResolutionSource.ACCOUNT_ATTRIBUTE), null);
    }

    @Test
    void mapsIdentityEdgeWithProvenanceNull() {
        IdentityEntitlementRow row = IdentityEntitlementRowMapper
                .map(assignment("7f00010198421229819849ebc9ef0c71", "7f00010198421229819849ebc9ef0c72"))
                .orElseThrow();

        assertEquals("7f000101-9842-1229-8198-49ebc9ef0c72", row.identityId());
        assertEquals("Alexander Evans", row.identityDisplayName());
        assertEquals("7f000101-9842-1229-8198-49c815b90bfc", row.applicationId());
        assertEquals("7f000101-9842-1229-8198-4a0000000001", row.entitlementId());
        assertEquals("GroupA-value", row.entitlementValue());
        assertEquals("groups", row.sourceAttribute());
        assertEquals("RESOLVED", row.resolutionStatus());
        // Authoritative provenance is NOT provided by the current source -> NULL, never invented.
        assertNull(row.source());
        assertNull(row.assigner());
        assertNull(row.assignedDate());
        assertNull(row.endDate());
        assertNull(row.aggregationState());
        assertNull(row.grantedByRole());
    }

    @Test
    void assignmentWithoutIdentityIsSkipped() {
        assertTrue(IdentityEntitlementRowMapper
                .map(assignment("7f00010198421229819849ebc9ef0c71", null)).isEmpty());
    }

    @Test
    void duplicateIdentityEdgesCollapseToOne() {
        // Same identity holds the same value via two different accounts of the same app/attr.
        List<AccountEntitlementAssignment> two = List.of(
                assignment("7f00010198421229819849ebc9ef0c71", "7f00010198421229819849ebc9ef0c72"),
                assignment("7f00010198421229819849ebc9ef0c99", "7f00010198421229819849ebc9ef0c72"));

        List<IdentityEntitlementRow> rows = IdentityEntitlementPersistenceService.buildDistinctRows(two);
        assertEquals(1, rows.size());
    }
}
