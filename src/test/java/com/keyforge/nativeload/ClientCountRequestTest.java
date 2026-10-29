package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every plugin client whose endpoint can count its population asks for the count only when told to: a page call
 * ({@code fetchPage(start, limit)}) never sends {@code includeCount}, so the plugin returns the page without
 * counting the whole table. Uses a recording {@link IiqSessionClient} (no network, no IIQ).
 */
class ClientCountRequestTest {

    /** Records every plugin GET's params; never opens a connection. */
    private static final class RecordingSession extends IiqSessionClient {
        final List<Map<String, String>> params = new ArrayList<>();

        RecordingSession() {
            super("http://localhost:1/identityiq", "u", "p", null);
        }

        @Override
        public void warmCsrfToken() {
            // no session needed
        }

        @Override
        public String get(String path, Map<String, String> queryParams) {
            params.add(new LinkedHashMap<>(queryParams));
            return "{}";
        }
    }

    /** A client's two page calls: without a count (the page read) and with one (a full read's first page). */
    private interface Calls {
        void page(RecordingSession s);

        void counted(RecordingSession s);
    }

    private static Calls calls(Function<RecordingSession, ? extends Object> client,
                               java.util.function.BiConsumer<Object, Boolean> fetch) {
        return new Calls() {
            public void page(RecordingSession s) {
                fetch.accept(client.apply(s), null);
            }

            public void counted(RecordingSession s) {
                fetch.accept(client.apply(s), Boolean.TRUE);
            }
        };
    }

    @Test
    void everyCountingEndpointClientAsksForTheCountOnlyWhenTold() {
        Map<String, Calls> clients = new LinkedHashMap<>();
        clients.put("account-entitlements", calls(NativeAccountEntitlementClient::new, (c, n) -> {
            NativeAccountEntitlementClient x = (NativeAccountEntitlementClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("audit-events", calls(NativeAuditEventClient::new, (c, n) -> {
            NativeAuditEventClient x = (NativeAuditEventClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("certifications", calls(NativeCertificationClient::new, (c, n) -> {
            NativeCertificationClient x = (NativeCertificationClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("certification-entities", calls(NativeCertificationEntityClient::new, (c, n) -> {
            NativeCertificationEntityClient x = (NativeCertificationEntityClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("certification-items", calls(NativeCertificationItemClient::new, (c, n) -> {
            NativeCertificationItemClient x = (NativeCertificationItemClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("identity-entitlements", calls(NativeIdentityEntitlementClient::new, (c, n) -> {
            NativeIdentityEntitlementClient x = (NativeIdentityEntitlementClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("identity-requests", calls(NativeIdentityRequestClient::new, (c, n) -> {
            NativeIdentityRequestClient x = (NativeIdentityRequestClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("identity-roles", calls(NativeIdentityRoleClient::new, (c, n) -> {
            NativeIdentityRoleClient x = (NativeIdentityRoleClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("policy-constraints", calls(NativePolicyConstraintClient::new, (c, n) -> {
            NativePolicyConstraintClient x = (NativePolicyConstraintClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("provisioning-transactions", calls(NativeProvisioningTxnClient::new, (c, n) -> {
            NativeProvisioningTxnClient x = (NativeProvisioningTxnClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("workitem-archives", calls(NativeWorkItemArchiveClient::new, (c, n) -> {
            NativeWorkItemArchiveClient x = (NativeWorkItemArchiveClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("workitems", calls(NativeWorkItemClient::new, (c, n) -> {
            NativeWorkItemClient x = (NativeWorkItemClient) c;
            if (n == null) x.fetchPage(0, 50); else x.fetchPage(0, 50, true);
        }));
        clients.put("role-relationships", calls(NativeRoleRelationshipClient::new, (c, n) -> {
            NativeRoleRelationshipClient x = (NativeRoleRelationshipClient) c;
            if (n == null) x.fetchPage(0, 50, "run"); else x.fetchPage(0, 50, "run", true);
        }));
        for (String path : List.of(NativeAccessHistoryClient.ENTITLEMENT_CAPTURES_PATH,
                NativeAccessHistoryClient.IDENTITY_EVENTS_PATH, NativeAccessHistoryClient.ROLE_EVENTS_PATH,
                NativeAccessHistoryClient.CERTIFICATIONS_PATH)) {
            clients.put(path, calls(NativeAccessHistoryClient::new, (c, n) -> {
                NativeAccessHistoryClient x = (NativeAccessHistoryClient) c;
                if (n == null) x.fetchPage(path, 0, 50); else x.fetchPage(path, 0, 50, true);
            }));
        }

        assertEquals(17, clients.size(), "all 17 counting plugin endpoints are covered");
        for (Map.Entry<String, Calls> e : clients.entrySet()) {
            RecordingSession page = new RecordingSession();
            e.getValue().page(page);
            assertFalse(page.params.get(0).containsKey("includeCount"), e.getKey() + ": a page call never counts");

            RecordingSession counted = new RecordingSession();
            e.getValue().counted(counted);
            assertEquals("true", counted.params.get(0).get("includeCount"), e.getKey() + ": asks when told");
        }
    }
}
