package com.keyforge.iiq.workitem;

/** Thrown when a Work Item cannot be safely mapped to a migration row. */
public class WorkItemMappingException extends RuntimeException {

    public WorkItemMappingException(String message) {
        super(message);
    }
}
