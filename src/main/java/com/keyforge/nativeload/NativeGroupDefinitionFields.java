package com.keyforge.nativeload;

/**
 * JSON wire contract for the native GroupDefinition payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeGroupDefinitionWire} and consumed here. Keep in lock-step.
 */
final class NativeGroupDefinitionFields {

    private NativeGroupDefinitionFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String FACTORY_ID = "factoryId";
    static final String FACTORY_NAME = "factoryName";
    static final String FILTER_EXPRESSION = "filterExpression";
    static final String IS_PRIVATE = "isPrivate";
    static final String INDEXED = "indexed";
    static final String NULL_GROUP = "nullGroup";
    static final String NAME_UNIQUE = "nameUnique";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String LAST_REFRESH = "lastRefresh";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
