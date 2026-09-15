package com.keyforge.iiq.workflow;

/**
 * A SailPoint IdentityIQ Workflow definition (SCIM {@code Workflow} resource). The SCIM
 * schema exposes only the definition identity — {@code id, name, type, handler, description}
 * (verified live) — NOT the approval scheme/mode/levels/escalation/e-signature configuration,
 * which lives in the workflow XML (a future authoritative JDBC/plugin source).
 *
 * @param id           SCIM resource id
 * @param name         workflow name
 * @param type         workflow type (e.g. Subprocess, LCMProvisioning)
 * @param handler      workflow handler class
 * @param description  description
 * @param created      SCIM meta.created (raw)
 * @param lastModified SCIM meta.lastModified (raw)
 */
public record WorkflowDefinition(
        String id,
        String name,
        String type,
        String handler,
        String description,
        String created,
        String lastModified) {
}
