package com.keyforge.iiq.workflow;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code kf_workflow_definition} table. Populated from SCIM
 * {@code /Workflows} (id/name/type/handler/description + meta timestamps). The PDF's richer
 * approval-configuration fields ({@code approval_scheme, approval_mode, approval_levels,
 * escalation, esignature}) are present but always NULL here — SCIM does not expose them;
 * their authoritative source is the workflow XML (JDBC/plugin), deferred as future work.
 * They are NEVER reconstructed from indirect data.
 */
public record WorkflowRow(
        String workflowid,
        String name,
        String type,
        String handler,
        String description,
        String approvalScheme,
        String approvalMode,
        String approvalLevels,
        String escalation,
        String esignature,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
