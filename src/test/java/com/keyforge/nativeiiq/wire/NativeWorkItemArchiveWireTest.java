package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeWorkItemArchiveExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkItemArchiveRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeWorkItemArchiveWireTest {
    @Test
    void envelopeIsJsonObjectShapeWithSourceCountAndArchiveIdentity() {
        NativeWorkItemArchiveExtractionResult result = new NativeWorkItemArchiveExtractionResult(
                "IdentityIQ", "run-1", "sailpoint.object.WorkItemArchive", Instant.EPOCH);
        result.setSourceCount(1);
        NativeWorkItemArchiveRow row = new NativeWorkItemArchiveRow();
        row.setSourceId("archive-id");
        row.setWorkItemId("original-work-item-id");
        row.setArchived(Instant.parse("2026-09-01T10:11:12Z"));
        row.setSrcEventTs(row.getArchived());
        result.getWorkItemArchives().add(row);

        Map<String, Object> envelope = NativeWorkItemArchiveWire.envelope(result, 0, 100);
        assertEquals(1, envelope.get("sourceCount"));
        assertEquals(1, envelope.get("returned"));
        assertTrue(envelope.get("rows") instanceof java.util.List<?>);
        Object responseEntity = envelope;
        assertFalse(responseEntity instanceof String);
        @SuppressWarnings("unchecked")
        Map<String, Object> wireRow = (Map<String, Object>) ((java.util.List<?>) envelope.get("rows")).get(0);
        assertEquals("archive-id", wireRow.get("sourceId"));
        assertEquals("original-work-item-id", wireRow.get("workItemId"));
        assertEquals("2026-09-01T10:11:12Z", wireRow.get("srcEventTs"));
    }
}
