package com.keyforge.nativeload;

/**
 * JSON wire contract for the native TaskResult payload — the field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeTaskResultWire} and consumed here. Keep in lock-step.
 */
final class NativeTaskResultFields {

    private NativeTaskResultFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String COMPLETION_STATUS = "completionStatus";
    static final String DEFINITION_NAME = "definitionName";
    static final String LAUNCHER = "launcher";
    static final String HOST = "host";
    static final String TARGET_NAME = "targetName";
    static final String TARGET_CLASS = "targetClass";
    static final String TARGET_ID = "targetId";
    static final String SCHEDULE = "schedule";
    static final String PROGRESS = "progress";
    static final String PERCENT_COMPLETE = "percentComplete";
    static final String RUN_LENGTH = "runLength";
    static final String PENDING_SIGNOFFS = "pendingSignoffs";
    static final String PARTITIONED = "partitioned";
    static final String TERMINATE_REQUESTED = "terminateRequested";
    static final String COMPLETE = "complete";
    static final String LAUNCHED = "launched";
    static final String COMPLETED = "completed";
    static final String EXPIRATION = "expiration";
    static final String VERIFIED = "verified";
    static final String MESSAGES = "messages";
    static final String ATTRIBUTES = "attributes";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
