package com.keyforge.iiq.nativeparquet;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentParquetRowSourceTest {

    @Test
    void mapsEachSupportedDatasetToExistingServiceAndAcceptsItsBusinessColumns() throws Exception {
        for (NativeParquetDatasets.Def d : NativeParquetDatasets.all()) {
            String service = AgentParquetRowSource.serviceFor(d.parquetName());
            if ("kf_event_link".equals(d.parquetName())) {
                assertEquals(null, service);
                continue;
            }
            assertTrue(service != null, d.parquetName());
            Map<String, Object> row = new LinkedHashMap<>();
            d.businessColumns().forEach(c -> row.put(c, null));
            row.put("source_id", "550e8400-e29b-41d4-a716-446655440000");
            row.put("value_json", null);
            AgentParquetRowSource source = new AgentParquetRowSource((actual, dataset) -> {
                assertEquals(service, actual);
                assertEquals(d, dataset);
                return List.of(row);
            });
            assertEquals(1, source.read(d).size(), d.parquetName());
        }
    }

    @Test
    void rejectsServiceRowsMissingAnyBusinessColumnAndSkipsDatabaseOnlyEventLink() {
        NativeParquetDatasets.Def audit = NativeParquetDatasets.all().stream()
                .filter(d -> d.parquetName().equals("kf_audit_event")).findFirst().orElseThrow();
        AgentParquetRowSource missing = new AgentParquetRowSource((service, d) -> List.of(Map.of("source_id", "x")));
        assertThrows(IllegalStateException.class, () -> missing.read(audit));
        NativeParquetDatasets.Def link = NativeParquetDatasets.all().stream()
                .filter(d -> d.parquetName().equals("kf_event_link")).findFirst().orElseThrow();
        assertTrue(assertThrows(IllegalStateException.class, () -> missing.read(link)).getMessage().contains("SKIPPED"));
    }
}
