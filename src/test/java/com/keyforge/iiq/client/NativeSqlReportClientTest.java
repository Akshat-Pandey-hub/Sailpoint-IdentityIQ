package com.keyforge.iiq.client;

import com.keyforge.nativeload.NativeSqlReportClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSqlReportClientTest {

    private static final class StubSession extends IiqSessionClient {
        String path;
        Map<String, String> params;
        boolean warmed;

        StubSession() {
            super("https://example.invalid/identityiq", "test-user", "test-password", null);
        }

        @Override
        public void warmCsrfToken() {
            warmed = true;
        }

        @Override
        public String get(String path, Map<String, String> queryParams) {
            this.path = path;
            this.params = new LinkedHashMap<>(queryParams);
            return "{}";
        }
    }

    @Test
    void sendsReportFiltersAndPagingToThePlugin() {
        StubSession session = new StubSession();
        NativeSqlReportClient client = new NativeSqlReportClient(session);
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("user_id", "alice");

        client.fetch("entitlementAssignment", filters, 25, 10);

        assertTrue(session.warmed);
        assertEquals(NativeSqlReportClient.SQL_REPORT_PATH + "entitlementAssignment", session.path);
        assertEquals("alice", session.params.get("user_id"));
        assertEquals("25", session.params.get("start"));
        assertEquals("10", session.params.get("limit"));
    }

    @Test
    void omittedPagingRemainsOmittedForLegacyFullReportBehavior() {
        StubSession session = new StubSession();
        new NativeSqlReportClient(session).fetch("workgroupMembers", null, null, null);
        assertTrue(session.params.isEmpty());
    }

    @Test
    void sendsModifiedAfterOnlyWhenNonBlank() {
        StubSession bounded = new StubSession();
        new NativeSqlReportClient(bounded).fetch("workgroupMembers", null, 0, 10, "2026-10-12T10:00:00Z");
        assertEquals("2026-10-12T10:00:00Z", bounded.params.get("modifiedAfter"));

        for (String absent : new String[]{null, "", "  "}) {
            StubSession plain = new StubSession();
            new NativeSqlReportClient(plain).fetch("workgroupMembers", null, 0, 10, absent);
            assertTrue(!plain.params.containsKey("modifiedAfter"));
        }
    }
}
