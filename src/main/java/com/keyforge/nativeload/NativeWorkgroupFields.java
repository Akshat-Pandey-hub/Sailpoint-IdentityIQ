package com.keyforge.nativeload;

/**
 * JSON wire contract for the native Workgroup payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeWorkgroupWire} and consumed here. Keep in lock-step.
 */
final class NativeWorkgroupFields {

    private NativeWorkgroupFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String DISPLAY_NAME = "displayName";
    static final String DISPLAYABLE_NAME = "displayableName";
    static final String EMAIL = "email";
    static final String TYPE = "type";
    static final String DESCRIPTION = "description";
    static final String NOTIFICATION_OPTION = "notificationOption";
    static final String INACTIVE = "inactive";
    static final String WORKGROUP = "workgroup";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String CAPABILITIES = "capabilities";
    static final String ATTRIBUTES = "attributes";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
