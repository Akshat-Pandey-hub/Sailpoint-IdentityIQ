package com.keyforge.iiq.workflow;

/** Thrown when a Workflow cannot be mapped to a {@code kf_workflow_definition} row (e.g. a
 * missing/non-UUID id). Reported and skipped, never invented. */
public class WorkflowMappingException extends RuntimeException {
    public WorkflowMappingException(String message) {
        super(message);
    }
}
