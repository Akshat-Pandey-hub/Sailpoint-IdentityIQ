package com.keyforge.nativeload;

/** JSON wire contract for the native Policy payload. Keep in lock-step with the wire. */
final class NativePolicyFields {

    private NativePolicyFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String TYPE_KEY = "typeKey";
    static final String DESCRIPTION = "description";
    static final String DESCRIPTIONS = "descriptions";
    static final String EXECUTOR = "executor";
    static final String VIOLATION_OWNER_ID = "violationOwnerId";
    static final String VIOLATION_OWNER_NAME = "violationOwnerName";
    static final String CONSTRAINT_COUNT = "constraintCount";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
