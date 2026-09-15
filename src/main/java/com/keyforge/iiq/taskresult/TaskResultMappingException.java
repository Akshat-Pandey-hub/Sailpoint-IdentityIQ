package com.keyforge.iiq.taskresult;

/** Thrown when a TaskResult cannot be mapped (e.g. a missing/non-UUID id).
 * Reported and skipped, never invented. */
public class TaskResultMappingException extends RuntimeException {
    public TaskResultMappingException(String message) {
        super(message);
    }
}
