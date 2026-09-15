package com.keyforge.iiq.workflow;

import com.keyforge.iiq.client.IiqApiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SCIM /Workflows parsing + mapping (approval-config NULL/deferred). */
class WorkflowTest {

    private static final String WF =
            "{\"id\":\"7f0001019714166881971476886b018c\",\"name\":\"Do Provisioning Forms\","
            + "\"type\":\"Subprocess\",\"handler\":\"sailpoint.api.StandardWorkflowHandler\","
            + "\"description\":\"desc\",\"meta\":{\"created\":\"2025-05-28T01:16:41.963Z\","
            + "\"lastModified\":\"2025-05-28T01:17:13.253Z\"}}";

    private static IiqApiClient fake(String body) {
        return new IiqApiClient() {
            @Override public String get(String path, Map<String, String> q) {
                assertEquals(WorkflowService.WORKFLOWS_PATH, path);
                return body;
            }
        };
    }

    @Test
    void parsesWorkflowDefinitions() {
        List<WorkflowDefinition> wfs = new WorkflowService(
                fake("{\"totalResults\":1,\"Resources\":[" + WF + "]}")).getAllWorkflows();
        assertEquals(1, wfs.size());
        assertEquals("Do Provisioning Forms", wfs.get(0).name());
        assertEquals("Subprocess", wfs.get(0).type());
    }

    @Test
    void emptyResultYieldsNoWorkflows() {
        assertTrue(new WorkflowService(fake("{\"totalResults\":0,\"Resources\":[]}"))
                .getAllWorkflows().isEmpty());
    }

    @Test
    void mapsWithApprovalConfigNull() {
        WorkflowDefinition wf = new WorkflowDefinition("7f0001019714166881971476886b018c",
                "Do Provisioning Forms", "Subprocess", "handler.Class", "desc",
                "2025-05-28T01:16:41.963Z", "2025-05-28T01:17:13.253Z");
        WorkflowRow row = WorkflowRowMapper.map(wf);
        assertEquals("7f000101-9714-1668-8197-1476886b018c", row.workflowid());
        assertEquals("Subprocess", row.type());
        assertEquals("handler.Class", row.handler());
        assertEquals(2025, row.createdAt().getYear());
        // approval-config not exposed by SCIM -> NULL
        assertNull(row.approvalScheme());
        assertNull(row.approvalMode());
        assertNull(row.approvalLevels());
        assertNull(row.escalation());
        assertNull(row.esignature());
    }
}
