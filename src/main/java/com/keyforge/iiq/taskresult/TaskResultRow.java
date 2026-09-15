package com.keyforge.iiq.taskresult;

import java.time.LocalDateTime;

/**
 * A row of {@code kf_task_result} (IdentityIQ TaskResult; PDF §8 extraction-provenance ledger).
 * Raw IIQ id preserved in {@code sourceId}; {@code launched}/{@code completed} are the SCIM ISO-8601
 * instants normalized to UTC; {@code messagesJson} is the raw messages array for JSONB storage.
 */
public record TaskResultRow(
        String taskresultid,
        String sourceId,
        String name,
        String type,
        String taskDefinition,
        String completionStatus,
        String host,
        String launcher,
        LocalDateTime launched,
        LocalDateTime completed,
        Boolean partitioned,
        Boolean terminated,
        Integer pendingSignoffs,
        String messagesJson) {
}
