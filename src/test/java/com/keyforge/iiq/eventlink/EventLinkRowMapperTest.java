package com.keyforge.iiq.eventlink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies deterministic PK, canonical audit id, verbatim raw-target preservation, and that
 * resolved vs unresolved results are carried faithfully into the row (no invented ids).
 */
class EventLinkRowMapperTest {

    private static final String RAW_AUDIT_ID = "7f0001019f061fdc819f06e6cb170029";
    private static final String CANON_AUDIT_ID = "7f000101-9f06-1fdc-819f-06e6cb170029";
    private static final String IDENTITY_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void mapsResolvedIdentityLinkWithDeterministicStablePk() {
        EventLinkResolver.Result res = new EventLinkResolver.Result(
                EventLinkResolver.Status.RESOLVED, "Identity", IDENTITY_ID, null, "usr.username|displayname");
        EventLinkRow row = EventLinkRowMapper.map(RAW_AUDIT_ID, "Alice Martin", res);

        assertEquals(CANON_AUDIT_ID, row.auditEventId());
        assertEquals("AuditEvent", row.sourceObjectType());
        assertEquals("Identity", row.targetObjectType());
        assertEquals(IDENTITY_ID, row.targetObjectId());
        assertEquals("Alice Martin", row.targetRaw());        // preserved verbatim
        assertEquals("RESOLVED", row.linkStatus());
        // deterministic + stable across calls
        assertEquals(row.id(), EventLinkRowMapper.map(RAW_AUDIT_ID, "Alice Martin", res).id());
    }

    @Test
    void mapsUnresolvedLinkKeepingRawTargetAndNullId() {
        EventLinkResolver.Result res = new EventLinkResolver.Result(
                EventLinkResolver.Status.UNRESOLVED, null, null, null, "no Identity match");
        EventLinkRow row = EventLinkRowMapper.map(RAW_AUDIT_ID, "ServerDown", res);

        assertEquals("UNRESOLVED", row.linkStatus());
        assertNull(row.targetObjectId());
        assertNull(row.targetObjectType());
        assertEquals("ServerDown", row.targetRaw());          // dangling reference preserved
        assertEquals(CANON_AUDIT_ID, row.auditEventId());
    }

    @Test
    void outOfScopeTypedKeepsHintAndRaw() {
        EventLinkResolver.Result res = new EventLinkResolver.Result(
                EventLinkResolver.Status.OUT_OF_SCOPE_TYPE, null, null, "Application", "out-of-scope");
        EventLinkRow row = EventLinkRowMapper.map(RAW_AUDIT_ID, "Application: corp directory-LDAP-Target", res);

        assertEquals("OUT_OF_SCOPE_TYPE", row.linkStatus());
        assertEquals("Application", row.targetTypeHint());
        assertEquals("Application: corp directory-LDAP-Target", row.targetRaw());
        assertNull(row.targetObjectId());
    }

    @Test
    void missingAuditIdIsReportedNotInvented() {
        EventLinkResolver.Result res = new EventLinkResolver.Result(
                EventLinkResolver.Status.UNRESOLVED, null, null, null, "x");
        assertThrows(EventLinkMappingException.class, () -> EventLinkRowMapper.map(null, "X", res));
    }
}
