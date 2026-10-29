package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** SQL reports send a query timeout only when KFAGENT_IIQ_TIMEOUT_SECONDS is set; a caller can never set it. */
class NativeSqlReportClientTimeoutTest {

    private static final class RecordingSession extends IiqSessionClient {
        final List<Map<String, String>> params = new ArrayList<>();
        final Integer configured;

        RecordingSession(Integer configured) {
            super("http://localhost:1/identityiq", "u", "p", null);
            this.configured = configured;
        }

        @Override
        public Integer configuredTimeoutSeconds() {
            return configured;
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

    @Test
    void notConfiguredSendsNoTimeoutSoThePluginKeepsItsDefault() {
        RecordingSession s = new RecordingSession(null);
        new NativeSqlReportClient(s).fetch("workgroupMembers", null, 0, 2);
        assertEquals(Map.of("start", "0", "limit", "2"), s.params.get(0));
    }

    @Test
    void configuredSendsTheTimeoutMinusTheMargin() {
        RecordingSession s = new RecordingSession(300);
        new NativeSqlReportClient(s).fetch("workgroupMembers", null, 0, 2);
        assertEquals("290", s.params.get(0).get("queryTimeoutSeconds"));
    }

    @Test
    void aCallerCannotSetTheTimeoutThroughAFilter() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("queryTimeoutSeconds", "1");
        RecordingSession unset = new RecordingSession(null);
        new NativeSqlReportClient(unset).fetch("workgroupMembers", filters, 0, 2);
        assertFalse(unset.params.get(0).containsKey("queryTimeoutSeconds"));

        RecordingSession set = new RecordingSession(120);
        new NativeSqlReportClient(set).fetch("workgroupMembers", filters, 0, 2);
        assertEquals("110", set.params.get(0).get("queryTimeoutSeconds"));
    }
}
