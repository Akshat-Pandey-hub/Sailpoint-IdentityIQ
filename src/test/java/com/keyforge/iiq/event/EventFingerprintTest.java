package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The fingerprint + event-id are deterministic and encode the PDF §7.3 dedup identity
 * {@code (src_system, src_object_type, src_object_id, event fingerprint)}. A changed business
 * attribute must yield a different fingerprint and therefore a different event id (late correction =
 * new appended event, never a mutation).
 */
class EventFingerprintTest {

    @Test
    void fingerprintIsDeterministicAndOrderSensitive() {
        assertEquals(EventFingerprint.fingerprint("a", "b", "c"),
                EventFingerprint.fingerprint("a", "b", "c"));
        assertNotEquals(EventFingerprint.fingerprint("a", "b", "c"),
                EventFingerprint.fingerprint("a", "c", "b"));
    }

    @Test
    void fingerprintIsNullSafeAndTrimmed() {
        assertEquals(EventFingerprint.fingerprint(null, "x"),
                EventFingerprint.fingerprint("", "  x  "));
    }

    @Test
    void eventIdIsDeterministicNameUuidOfTheDedupIdentity() {
        String fp = EventFingerprint.fingerprint("EntitlementAdd", "spadmin", "Identity:Joe", "Sep 1, 2026, 1:00 AM");
        String id = EventFingerprint.eventId("IdentityIQ", "sailpoint.object.AuditEvent", "audit-1", fp);
        String expected = UUID.nameUUIDFromBytes(
                ("IdentityIQ|sailpoint.object.AuditEvent|audit-1|" + fp).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        assertEquals(expected, id);
        assertEquals(id, EventFingerprint.eventId("IdentityIQ", "sailpoint.object.AuditEvent", "audit-1", fp));
    }

    @Test
    void changedBusinessAttributeProducesNewEventId() {
        String base = EventFingerprint.fingerprint("txn-1", "Add", "LCM", "Committed", "Success", "x");
        String changed = EventFingerprint.fingerprint("txn-1", "Add", "LCM", "Failed", "Success", "x");
        assertNotEquals(base, changed);
        assertNotEquals(
                EventFingerprint.eventId("IdentityIQ", "sailpoint.object.ProvisioningTransaction", "txn-1", base),
                EventFingerprint.eventId("IdentityIQ", "sailpoint.object.ProvisioningTransaction", "txn-1", changed),
                "a changed attribute must create a NEW event id, not reuse the old one");
    }

    @Test
    void caseIsPreservedNotCollapsed() {
        // DNs and names are case-sensitive; distinct case must remain distinct events.
        assertNotEquals(EventFingerprint.fingerprint("CN=Devs"), EventFingerprint.fingerprint("cn=devs"));
    }
}
