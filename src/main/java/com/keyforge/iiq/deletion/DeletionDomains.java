package com.keyforge.iiq.deletion;

/**
 * Registry of CSS domains wired for deletion detection. {@code task_result} is the first (a volatile
 * IIQ object that gets purged, so upsert-only persistence accumulates stale rows). Additional domains
 * are added here plus a thin per-domain command that supplies the authoritative source id set.
 */
public final class DeletionDomains {

    public static final DeletionDomain TASK_RESULT =
            new DeletionDomain("task_result", "kf_task_result", "taskresultid");

    private DeletionDomains() {
    }
}
