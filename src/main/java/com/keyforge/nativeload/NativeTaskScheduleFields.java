package com.keyforge.nativeload;

/**
 * JSON wire contract for the native TaskSchedule payload — the field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeTaskScheduleWire} and consumed here. Keep in lock-step.
 */
final class NativeTaskScheduleFields {

    private NativeTaskScheduleFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String DESCRIPTION = "description";
    static final String DEFINITION_NAME = "definitionName";
    static final String STATE = "state";
    static final String NEW_STATE = "newState";
    static final String LAUNCHER = "launcher";
    static final String HOST = "host";
    static final String LAST_LAUNCH_ERROR = "lastLaunchError";
    static final String DELETE_ON_FINISH = "deleteOnFinish";
    static final String LAST_EXECUTION = "lastExecution";
    static final String NEXT_EXECUTION = "nextExecution";
    static final String NEXT_ACTUAL_EXECUTION = "nextActualExecution";
    static final String RESUME_DATE = "resumeDate";
    static final String CRON_EXPRESSIONS = "cronExpressions";
    static final String ARGUMENTS = "arguments";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
