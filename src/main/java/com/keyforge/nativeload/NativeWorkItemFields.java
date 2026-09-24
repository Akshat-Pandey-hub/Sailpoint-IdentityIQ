package com.keyforge.nativeload;

/** JSON wire contract for native WorkItem — produced by NativeWorkItemWire, consumed here. */
final class NativeWorkItemFields {

    private NativeWorkItemFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String STATE = "state";
    static final String LEVEL = "level";
    static final String REQUESTER_ID = "requesterId";
    static final String REQUESTER_NAME = "requesterName";
    static final String ASSIGNEE_ID = "assigneeId";
    static final String ASSIGNEE_NAME = "assigneeName";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String COMPLETER = "completer";
    static final String COMPLETION_COMMENTS = "completionComments";
    static final String HANDLER = "handler";
    static final String NOTIFICATION_NAME = "notificationName";
    static final String IDENTITY_REQUEST_ID = "identityRequestId";
    static final String TARGET_ID = "targetId";
    static final String TARGET_NAME = "targetName";
    static final String CERTIFICATION_ID = "certificationId";
    static final String CERTIFICATION_ENTITY_ID = "certificationEntityId";
    static final String CERTIFICATION_ITEM_ID = "certificationItemId";
    static final String ENTITY_TYPE = "entityType";
    static final String CERTIFICATION_RELATED = "certificationRelated";
    static final String WORKFLOW_CASE_ID = "workflowCaseId";
    static final String WORKFLOW_CASE_NAME = "workflowCaseName";
    static final String EXPIRATION = "expiration";
    static final String EXPIRATION_DATE = "expirationDate";
    static final String NOTIFICATION = "notification";
    static final String WAKE_UP_DATE = "wakeUpDate";
    static final String ESCALATION_COUNT = "escalationCount";
    static final String REMINDERS = "reminders";
    static final String REMINDERS_SENT = "remindersSent";
    static final String EXPIRED = "expired";
    static final String EXPIRABLE = "expirable";
    static final String APPROVAL_SET_ITEM_COUNT = "approvalSetItemCount";
    static final String COMMENTS = "comments";
    static final String SIGN_OFFS = "signOffs";
    static final String OWNER_HISTORY = "ownerHistory";
    static final String APPROVAL_SET_ITEMS = "approvalSetItems";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
