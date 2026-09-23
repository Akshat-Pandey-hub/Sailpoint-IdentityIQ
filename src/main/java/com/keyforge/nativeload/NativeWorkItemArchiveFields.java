package com.keyforge.nativeload;

/**
 * JSON wire contract for the native WorkItemArchive payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeWorkItemArchiveWire} and consumed here. Keep in lock-step.
 */
final class NativeWorkItemArchiveFields {

    private NativeWorkItemArchiveFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String WORK_ITEM_ID = "workItemId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String STATE = "state";
    static final String LEVEL = "level";
    static final String REQUESTER = "requester";
    static final String ASSIGNEE = "assignee";
    static final String OWNER_NAME = "ownerName";
    static final String COMPLETER = "completer";
    static final String COMPLETION_COMMENTS = "completionComments";
    static final String SIGNED = "signed";
    static final String TARGET_CLASS = "targetClass";
    static final String TARGET_ID = "targetId";
    static final String TARGET_NAME = "targetName";
    static final String IDENTITY_REQUEST_ID = "identityRequestId";
    static final String CERTIFICATION_ID = "certificationId";
    static final String CERTIFICATION_ENTITY_ID = "certificationEntityId";
    static final String CERTIFICATION_ITEM_ID = "certificationItemId";
    static final String ENTITY_TYPE = "entityType";

    static final String SIGN_OFFS = "signOffs";
    static final String COMMENTS = "comments";
    static final String OWNER_HISTORY = "ownerHistory";
    static final String SYSTEM_ATTRIBUTES = "systemAttributes";
    static final String ATTRIBUTES = "attributes";

    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String EXPIRATION = "expiration";
    static final String ARCHIVED = "archived";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String SRC_NATURAL_KEY = "srcNaturalKey";
    static final String SRC_EVENT_TS = "srcEventTs";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
