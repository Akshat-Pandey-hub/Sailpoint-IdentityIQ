package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage-2 incremental (objects with their own change time): the identity-request, provisioning-transaction,
 * work-item, task-result, certification-item and certification-entity clients forward an optional
 * {@code modifiedAfter} to the plugin and send exactly the previous request when it is absent or blank.
 */
class ChangedAfterClientTest {

    private static final String BOUND = "2026-10-12T10:00:00Z";

    private static final class RecordingSession extends IiqSessionClient {
        final List<Map<String, String>> params = new ArrayList<>();

        RecordingSession() {
            super("http://localhost:1/identityiq", "u", "p", null); // no real HttpClient: every network call is overridden
        }

        @Override
        public void warmCsrfToken() {
        }

        @Override
        public String get(String path, Map<String, String> queryParams) {
            params.add(new LinkedHashMap<>(queryParams));
            return "{}";
        }
    }

    /** {@code call(session, bound)} builds the client with the given bound (possibly null/blank) and fetches. */
    private static void check(BiConsumer<IiqSessionClient, String> call) {
        RecordingSession bounded = new RecordingSession();
        call.accept(bounded, BOUND);
        assertEquals(Map.of("start", "0", "limit", "50", "modifiedAfter", BOUND), bounded.params.get(0));

        for (String none : new String[]{null, "", "  "}) {
            RecordingSession plain = new RecordingSession();
            call.accept(plain, none);
            assertEquals(Map.of("start", "0", "limit", "50"), plain.params.get(0), "no bound -> unchanged request");
        }
    }

    @Test
    void identityRequestClient() {
        check((s, b) -> new NativeIdentityRequestClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void provisioningTransactionClient() {
        check((s, b) -> new NativeProvisioningTxnClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void workItemClient() {
        check((s, b) -> new NativeWorkItemClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void taskResultClient() {
        check((s, b) -> new NativeTaskResultClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void certificationItemClient() {
        check((s, b) -> new NativeCertificationItemClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void certificationEntityClient() {
        check((s, b) -> new NativeCertificationEntityClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void identityEntitlementClient() {
        check((s, b) -> new NativeIdentityEntitlementClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void accountEntitlementClient() {
        check((s, b) -> new NativeAccountEntitlementClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void certificationClient() { check((s, b) -> new NativeCertificationClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void groupDefinitionClient() { check((s, b) -> new NativeGroupDefinitionClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void policyClient() { check((s, b) -> new NativePolicyClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void violationClient() { check((s, b) -> new NativeViolationClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void workgroupClient() { check((s, b) -> new NativeWorkgroupClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void taskScheduleClient() { check((s, b) -> new NativeTaskScheduleClient(s).withModifiedAfter(b).fetchPage(0, 50)); }

    @Test
    void workflowClient() {
        for (String none : new String[]{null, "", "  "}) {
            RecordingSession plain = new RecordingSession();
            new NativeWorkflowClient(plain, "run-1").withModifiedAfter(none).fetch(0, 50);
            assertEquals(Map.of("start", "0", "limit", "50", "runId", "run-1"), plain.params.get(0));
        }
        RecordingSession bounded = new RecordingSession();
        new NativeWorkflowClient(bounded, "run-1").withModifiedAfter(BOUND).fetch(0, 50);
        assertEquals(Map.of("start", "0", "limit", "50", "runId", "run-1", "modifiedAfter", BOUND), bounded.params.get(0));
    }

    @Test
    void identityRoleClient() {
        check((s, b) -> new NativeIdentityRoleClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

    @Test
    void roleRelationshipClient() {
        for (String bound : new String[]{BOUND, null, "", "  "}) {
            RecordingSession session = new RecordingSession();
            new NativeRoleRelationshipClient(session).withModifiedAfter(bound).fetchPage(0, 50, "run-1");
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("start", "0"); expected.put("limit", "50"); expected.put("runId", "run-1");
            if (bound != null && !bound.isBlank()) expected.put("modifiedAfter", BOUND);
            assertEquals(expected, session.params.get(0));
        }
    }

    @Test
    void workgroupMembershipClient() {
        for (String bound : new String[]{BOUND, null, "", "  "}) {
            RecordingSession session = new RecordingSession();
            new NativeWorkgroupMembershipClient(session, "run-1").withModifiedAfter(bound).fetch(0, 50);
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("start", "0"); expected.put("limit", "50"); expected.put("runId", "run-1");
            if (bound != null && !bound.isBlank()) expected.put("modifiedAfter", BOUND);
            assertEquals(expected, session.params.get(0));
        }
    }

    @Test
    void policyConstraintClient() {
        check((s, b) -> new NativePolicyConstraintClient(s).withModifiedAfter(b).fetchPage(0, 50));
    }

}
