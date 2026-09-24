package com.keyforge.iiq.nativeevent;

import com.keyforge.iiq.event.EventRow;
import com.keyforge.iiq.event.EventType;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic native EVENT-zone derivation + explicit-id-only linking (no inference). */
class NativeEventDeriverTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 25, 10, 0, 0);

    private static NativeEventDeriver.ApprovalSrc approval(String owner, String req, String wi, LocalDateTime end) {
        return new NativeEventDeriver.ApprovalSrc("apr-1", req, owner, "Sec Admins", "finished",
                Boolean.TRUE, "alice", wi, T, end);
    }

    @Test
    void eventIdIsDeterministicAndRerunStable() {
        NativeEventDeriver.Derived a = NativeEventDeriver.deriveAll(
                List.of(approval("own-1", "req-1", "wi-1", T)), null, null, null, "run-A");
        NativeEventDeriver.Derived b = NativeEventDeriver.deriveAll(
                List.of(approval("own-1", "req-1", "wi-1", T)), null, null, null, "run-B-different");
        assertEquals(1, a.events.size());
        assertEquals(a.events.get(0).eventId(), b.events.get(0).eventId(), "same source ⇒ same event id across runs");
        assertEquals(a.events.get(0).eventId(), UUID.fromString(a.events.get(0).eventId()).toString());
    }

    @Test
    void approvalDecisionVsOpenByEndDate() {
        assertEquals(EventType.APPROVAL_DECISION, NativeEventDeriver.deriveAll(
                List.of(approval("o", "r", "w", T)), null, null, null, "run").events.get(0).eventType());
        assertEquals(EventType.APPROVAL_OPEN, NativeEventDeriver.deriveAll(
                List.of(approval("o", "r", "w", null)), null, null, null, "run").events.get(0).eventType());
    }

    @Test
    void linksComeOnlyFromExplicitIdColumns() {
        // all three ids present -> exactly three explicit links
        NativeEventDeriver.Derived all = NativeEventDeriver.deriveAll(
                List.of(approval("own-1", "req-1", "wi-1", T)), null, null, null, "run");
        assertEquals(3, all.links.size());
        assertTrue(all.links.stream().allMatch(l -> NativeEventLinkRow.EXPLICIT.equals(l.linkStatus())));
        assertTrue(all.links.stream().anyMatch(l -> "APPROVAL_OF_REQUEST".equals(l.linkType())
                && "IdentityRequest".equals(l.targetObjectType()) && "req-1".equals(l.targetObjectId())));

        // null owner + null workitem -> only the request link survives (no fabricated links)
        NativeEventDeriver.Derived some = NativeEventDeriver.deriveAll(
                List.of(approval(null, "req-1", null, T)), null, null, null, "run");
        assertEquals(1, some.links.size());
        assertEquals("APPROVAL_OF_REQUEST", some.links.get(0).linkType());
    }

    @Test
    void taskTargetNameAloneNeverCreatesLink() {
        // target id absent (only a name would exist) -> zero links
        NativeEventDeriver.TaskSrc noId = new NativeEventDeriver.TaskSrc(
                "tr-1", "Nightly Agg", "AccountAggregation", "Success", null, null, T, T);
        assertEquals(0, NativeEventDeriver.deriveAll(null, List.of(noId), null, null, "run").links.size());
        // explicit target id + class -> exactly one link
        NativeEventDeriver.TaskSrc withId = new NativeEventDeriver.TaskSrc(
                "tr-2", "Agg", "AccountAggregation", "Success", "sailpoint.object.Application", "app-9", T, T);
        NativeEventDeriver.Derived d = NativeEventDeriver.deriveAll(null, List.of(withId), null, null, "run");
        assertEquals(1, d.links.size());
        assertEquals("Application", d.links.get(0).targetObjectType());
        assertEquals("app-9", d.links.get(0).targetObjectId());
    }

    @Test
    void provisioningHasNoIdentityIdSoNoIdentityLinkButRequestLinkStands() {
        // kf_provisioning_txn has NO identity id column — only a name — so no Identity link is invented.
        NativeEventDeriver.ProvSrc p = new NativeEventDeriver.ProvSrc(
                "txn-1", "Modify", "IdentityRefresh", "committed", "success", "Bob Smith",
                "AD", "req-9", null, null, null, T);
        NativeEventDeriver.Derived d = NativeEventDeriver.deriveAll(null, null, List.of(p), null, "run");
        assertEquals(EventType.PROVISIONING_TXN, d.events.get(0).eventType());
        assertEquals(1, d.links.size());
        assertEquals("PROVISION_OF_REQUEST", d.links.get(0).linkType());
        assertEquals("IdentityRequest", d.links.get(0).targetObjectType());
    }

    @Test
    void eventCarriesNativeProvenanceAndInterface() {
        EventRow e = NativeEventDeriver.deriveAll(
                List.of(approval("o", "r", "w", T)), null, null, null, "run").events.get(0);
        assertEquals("apr-1", e.srcObjectId(), "native source record id preserved");
        assertEquals(NativeEventDeriver.SRC_INTERFACE, e.srcInterface());
        assertEquals("native_iiq_java_api", e.srcInterface());
        assertNotNull(e.eventFingerprint());
    }

    private static NativeEventDeriver.CertItemSrc certItem(Boolean actedUpon, String status, String ownerId) {
        return new NativeEventDeriver.CertItemSrc("ci-1", "cert-1", "ent-1", "Bob Smith", status,
                "Revoke", "reviewer1", Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, actedUpon, ownerId, T, T, T);
    }

    @Test
    void certificationDecisionOnlyWhenDecided() {
        // acted upon -> one CERTIFICATION_DECISION event
        NativeEventDeriver.Derived decided = NativeEventDeriver.deriveAll(
                null, null, null, null, List.of(certItem(Boolean.TRUE, "Remediated", "own-9")), "run");
        assertEquals(1, decided.events.size());
        assertEquals(NativeEventDeriver.CERTIFICATION_DECISION, decided.events.get(0).eventType());
        assertEquals("sailpoint.object.CertificationItem", decided.events.get(0).srcObjectType());
        // undecided (no acted_upon, no status) -> no event, no fabricated decision
        NativeEventDeriver.Derived undecided = NativeEventDeriver.deriveAll(
                null, null, null, null, List.of(certItem(Boolean.FALSE, null, "own-9")), "run");
        assertEquals(0, undecided.events.size());
    }

    @Test
    void certLinksUseExplicitIdsAndNeverTheIdentityName() {
        NativeEventDeriver.Derived d = NativeEventDeriver.deriveAll(
                null, null, null, null, List.of(certItem(Boolean.TRUE, "Remediated", "own-9")), "run");
        // certification_id, entity_id, owner_id -> 3 explicit links; identity NAME ("Bob Smith") is not a link
        assertEquals(3, d.links.size());
        assertTrue(d.links.stream().anyMatch(l -> "CERT_OF_CERTIFICATION".equals(l.linkType()) && "cert-1".equals(l.targetObjectId())));
        assertTrue(d.links.stream().anyMatch(l -> "CERT_ITEM_ENTITY".equals(l.linkType()) && "ent-1".equals(l.targetObjectId())));
        assertTrue(d.links.stream().anyMatch(l -> "CERT_OWNER".equals(l.linkType()) && "own-9".equals(l.targetObjectId())));
        assertTrue(d.links.stream().noneMatch(l -> "Bob Smith".equals(l.targetObjectId())));
        // null owner id -> only the two explicit-id links survive
        NativeEventDeriver.Derived noOwner = NativeEventDeriver.deriveAll(
                null, null, null, null, List.of(certItem(Boolean.TRUE, "Remediated", null)), "run");
        assertEquals(2, noOwner.links.size());
    }

    @Test
    void linkIdIsDeterministic() {
        String id1 = NativeEventDeriver.deriveAll(List.of(approval("o", "r", "w", T)), null, null, null, "run")
                .links.get(0).linkId();
        String id2 = NativeEventDeriver.deriveAll(List.of(approval("o", "r", "w", T)), null, null, null, "run2")
                .links.get(0).linkId();
        assertEquals(id1, id2);
        assertEquals(id1, UUID.fromString(id1).toString());
    }
}
