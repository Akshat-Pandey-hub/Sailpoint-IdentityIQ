package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the KF Agent Workflow-Definition read service: reuses the EXISTING native Workflow client +
 * parser over a fake page source (no live IIQ, no DB — proving the REST path never needs PostgreSQL),
 * returns our 9 DB-named business fields, excludes the workflowid PK + lineage/soft-delete, keeps the full
 * {@code definition} as real structured JSON (never stringified, never summarized), serializes timestamps as
 * ISO, and supports generic exact filtering on any scalar field. Mirrors the supplied "Do Provisioning
 * Forms" Subprocess record.
 */
class NativeWorkflowDefinitionRestServiceTest {

    private final NativeWorkflowDefinitionRestService svc = new NativeWorkflowDefinitionRestService();

    private static final String WF_1 = "7f0001019714166881971476886b018c";
    private static final String WF_2 = "bbbb1111cccc2222dddd3333eeee4444";

    /** Sample: "Do Provisioning Forms" Subprocess, null handler/description/task_type, a rich nested definition. */
    private static String workflow1() {
        return "{\"sourceId\":\"" + WF_1 + "\",\"name\":\"Do Provisioning Forms\",\"type\":\"Subprocess\","
                + "\"definition\":{"
                + "\"steps\":["
                + "{\"name\":\"Start\",\"transitions\":[{\"to\":\"Provision\"}]},"
                + "{\"name\":\"Provision\",\"action\":{\"call\":\"provisionProject\"},"
                + "\"approvals\":[{\"owner\":\"manager\"}]}"
                + "],"
                + "\"variables\":[{\"name\":\"plan\",\"initializer\":\"script\"}],"
                + "\"workItemConfig\":{\"renderer\":\"forms.xhtml\"},"
                + "\"configuration\":{\"trace\":\"true\"}},"
                + "\"created\":\"2025-05-28T01:16:41.963Z\",\"modified\":\"2025-05-28T01:17:13.253Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"extractionRunId\":\"run-1\"}";
    }

    /** Sample 2: a full Workflow with all scalar fields populated; used for filtering + windowing. */
    private static String workflow2() {
        return "{\"sourceId\":\"" + WF_2 + "\",\"name\":\"LCM Provisioning\",\"type\":\"Workflow\","
                + "\"handler\":\"sailpoint.engine.WorkflowHandler\",\"description\":\"Top level LCM\","
                + "\"taskType\":\"LCM\",\"definition\":{\"steps\":[{\"name\":\"Stop\"}]},"
                + "\"created\":\"2025-06-01T00:00:00Z\",\"modified\":\"2025-06-02T00:00:00Z\"}";
    }

    private static NativeWorkflowDefinitionRestService.PageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + workflow1() + "," + workflow2() + "]}" : "{\"rows\":[]}";
    }

    private static NativeWorkflowDefinitionRestService.PageSource emptySource() {
        return (start, limit) -> "{\"rows\":[]}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsOurFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(WF_1, r.get("source_id"));
        assertEquals("Do Provisioning Forms", r.get("name"));
        assertEquals("Subprocess", r.get("type"));
        assertEquals(9, r.size(), "exactly the 9 SailPoint-facing business fields");
        assertTrue(r.containsKey("definition"));
        assertTrue(r.containsKey("created_at"));
        assertTrue(r.containsKey("modified_at"));

        // excluded KeyForge/technical columns (PK is not a business field)
        assertFalse(r.containsKey("workflowid"));
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("src_object_id"));
        assertFalse(r.containsKey("src_natural_key"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
        assertFalse(r.containsKey("is_deleted"));
        assertFalse(r.containsKey("deleted_at"));
    }

    @Test
    void definitionStaysStructuredJsonNotStringifiedAndNullsPreserved() {
        Map<String, Object> r = svc.fetch(source(), null, null, null).get(0);

        Object def = r.get("definition");
        assertTrue(def instanceof JsonNode, "definition must be real JSON, not a stringified blob");
        JsonNode d = (JsonNode) def;
        assertTrue(d.isObject());
        // full structure preserved, not reduced to a summary
        assertTrue(d.get("steps").isArray());
        assertEquals(2, d.get("steps").size());
        assertEquals("Start", d.get("steps").get(0).get("name").asText());
        assertEquals("Provision", d.get("steps").get(1).get("name").asText());
        assertEquals("provisionProject", d.get("steps").get(1).get("action").get("call").asText());
        assertEquals("manager", d.get("steps").get(1).get("approvals").get(0).get("owner").asText());
        assertEquals("plan", d.get("variables").get(0).get("name").asText());
        assertEquals("forms.xhtml", d.get("workItemConfig").get("renderer").asText());
        assertEquals("true", d.get("configuration").get("trace").asText());

        // timestamps serialize as ISO strings
        assertEquals("2025-05-28T01:16:41.963Z", r.get("created_at"));
        assertEquals("2025-05-28T01:17:13.253Z", r.get("modified_at"));

        // null scalar fields preserved exactly
        assertNull(r.get("handler"));
        assertNull(r.get("description"));
        assertNull(r.get("task_type"));

        // ordinary text fields on the fully-populated record stay strings
        Map<String, Object> r2 = svc.fetch(source(), null, null, null).get(1);
        assertEquals("sailpoint.engine.WorkflowHandler", r2.get("handler"));
        assertEquals("Top level LCM", r2.get("description"));
        assertEquals("LCM", r2.get("task_type"));
    }

    @Test
    void scalarFiltersBySourceIdNameType() {
        assertEquals(1, svc.fetch(source(), f("source_id", WF_1), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "Do Provisioning Forms"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Subprocess"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "Workflow"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("task_type", "LCM"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "subprocess"), null, null).size(), "case-sensitive value");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("name", "LCM Provisioning", "type", "Workflow"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("name", "LCM Provisioning", "type", "Subprocess"), null, null).size());
    }

    @Test
    void unknownAndDefinitionFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // definition is structured JSON and is not a filterable scalar
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("definition", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("LCM Provisioning", win.get(0).get("name"));
        // filter first, then window
        assertEquals(1, svc.fetch(source(), f("type", "Subprocess"), 0, 1).size());
        assertEquals(0, svc.fetch(source(), f("name", "Do Provisioning Forms"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
