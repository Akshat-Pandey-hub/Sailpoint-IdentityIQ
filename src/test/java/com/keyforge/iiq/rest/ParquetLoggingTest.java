package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Parquet export's SERVICE/RESULT text: correct purpose and source, and a real summary instead of "N rows". */
class ParquetLoggingTest {

    @Test
    void exportRoutesHaveAPurposeAndSayTheyWriteFiles() {
        assertTrue(KfAgentServiceCatalog.purpose("parquet/export").startsWith("Parquet export: read the native datasets"));
        assertTrue(KfAgentServiceCatalog.purpose("parquet/export/kf_audit_event").contains("'kf_audit_event'"));
        assertTrue(KfAgentServiceCatalog.source("parquet/export").contains("writes Parquet files to PARQUET_OUT_DIR"));
        assertFalse(KfAgentServiceCatalog.purpose("parquet/export").startsWith("unknown endpoint"));
    }

    @Test
    void dataRoutesAreUnchanged() {
        assertEquals("IdentityIQ via the KeyForge plugin (read-only)", KfAgentServiceCatalog.source("identities"));
        assertFalse(KfAgentServiceCatalog.isParquetExport("identities"));
        assertFalse(KfAgentServiceCatalog.isParquetExport("parquet/exporter"));
    }

    @Test
    void exportResultSummarisesDatasetsRowsAndSkips() throws Exception {
        String body = "[{\"dataset\":\"kf_audit_event\",\"total\":508,\"inserted\":0,\"changed\":2,\"unchanged\":506,\"error\":null},"
                + "{\"dataset\":\"task_result\",\"total\":229,\"inserted\":3,\"changed\":0,\"unchanged\":226,\"error\":null},"
                + "{\"dataset\":\"kf_event_link\",\"total\":0,\"inserted\":0,\"changed\":0,\"unchanged\":0,"
                + "\"error\":\"SKIPPED: no KF Agent service\"}]";
        String summary = KfAgentRestServer.parquetExportSummary(new ObjectMapper().readTree(body));
        assertEquals("2 datasets written, 737 rows (new 3, changed 2, unchanged 732); "
                + "not written: kf_event_link (SKIPPED: no KF Agent service)", summary);
    }
}
