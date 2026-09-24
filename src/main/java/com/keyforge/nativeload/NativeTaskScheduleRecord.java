package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native TaskSchedule row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_task_schedule}. Scalars held directly; nested structures (cron expressions,
 * arguments) carried as pre-serialized JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeTaskScheduleRecord {

    String sourceId;
    String name;
    String description;
    String definitionName;
    String state;
    String newState;
    String launcher;
    String host;
    String lastLaunchError;

    Boolean deleteOnFinish;

    Instant lastExecution;
    Instant nextExecution;
    Instant nextActualExecution;
    Instant resumeDate;

    String cronExpressionsJson;
    String argumentsJson;

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
