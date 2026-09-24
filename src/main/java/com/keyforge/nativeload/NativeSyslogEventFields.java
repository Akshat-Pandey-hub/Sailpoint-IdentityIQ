package com.keyforge.nativeload;

/** JSON wire contract for the native SyslogEvent payload. Keep in lock-step with the wire. */
final class NativeSyslogEventFields {

    private NativeSyslogEventFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String SOURCE_ID = "sourceId";
    static final String QUICK_KEY = "quickKey";
    static final String EVENT_LEVEL = "eventLevel";
    static final String SERVER = "server";
    static final String USERNAME = "username";
    static final String THREAD = "thread";
    static final String LINE_NUMBER = "lineNumber";
    static final String MESSAGE = "message";
    static final String STACKTRACE = "stacktrace";
    static final String CREATED = "created";
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
