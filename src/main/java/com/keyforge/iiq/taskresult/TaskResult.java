package com.keyforge.iiq.taskresult;

/**
 * A SailPoint IdentityIQ TaskResult as returned by the SCIM v2 API
 * ({@code GET /scim/v2/TaskResults}, verified live — 168 records). Captures only the fields the SCIM
 * schema exposes (PDF §8: extraction provenance / data-freshness ledger, Domain 15). Nothing is
 * inferred; a field the source leaves absent stays null.
 *
 * <p>{@code launched}/{@code completed} are ISO-8601 UTC instants (e.g. "2025-07-27T04:00:28.298Z");
 * {@code messagesJson} is the raw {@code messages} JSON array, preserved verbatim for JSONB storage.
 *
 * @param id                the TaskResult id (32-char GUID)
 * @param name              task result name
 * @param type              task type (e.g. "System")
 * @param taskDefinition    the originating task definition name
 * @param completionStatus  Success / Error / Warning / Terminated / ...
 * @param host              the IIQ host that ran the task
 * @param launcher          the identity that launched it
 * @param launched          ISO-8601 UTC launch timestamp string
 * @param completed         ISO-8601 UTC completion timestamp string
 * @param partitioned       whether the task was partitioned
 * @param terminated        whether the task was terminated
 * @param pendingSignoffs   number of pending sign-offs
 * @param messagesJson      the raw {@code messages} JSON array (or null)
 */
public record TaskResult(
        String id,
        String name,
        String type,
        String taskDefinition,
        String completionStatus,
        String host,
        String launcher,
        String launched,
        String completed,
        Boolean partitioned,
        Boolean terminated,
        Integer pendingSignoffs,
        String messagesJson) {
}
