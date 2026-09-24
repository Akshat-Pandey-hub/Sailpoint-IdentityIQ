package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native TaskResult row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_task_result}. Scalars held directly; nested structures (messages, statistics
 * attributes) carried as pre-serialized JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeTaskResultRecord {

    String sourceId;
    String name;
    String type;
    String completionStatus;
    String definitionName;
    String launcher;
    String host;
    String targetName;
    String targetClass;
    String targetId;
    String schedule;
    String progress;

    Integer percentComplete;
    Integer runLength;
    Integer pendingSignoffs;

    Boolean partitioned;
    Boolean terminateRequested;
    Boolean complete;

    Instant launched;
    Instant completed;
    Instant expiration;
    Instant verified;

    String messagesJson;
    String attributesJson;

    Instant created;
    Instant modified;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }
}
