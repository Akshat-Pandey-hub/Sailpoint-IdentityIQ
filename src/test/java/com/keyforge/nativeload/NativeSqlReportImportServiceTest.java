package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSqlReportImportServiceTest {

    @Test
    void truncatedResponseFailsBeforeReplacingExistingRows() {
        String json = "{\"columns\":[\"identity_id\"],\"rows\":[{\"identity_id\":\"partial\"}],"
                + "\"truncated\":true}";
        NativeSqlReportImportService service = new NativeSqlReportImportService(
                () -> json, new NativeSqlReportRepository("public"));

        NativeImportException error = assertThrows(NativeImportException.class,
                () -> service.importAll(null, "run-1"));

        assertTrue(error.getMessage().contains("truncated"));
        assertTrue(error.getMessage().contains("left unchanged"));
    }
}
