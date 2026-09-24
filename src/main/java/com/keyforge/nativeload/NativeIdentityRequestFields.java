package com.keyforge.nativeload;

/** JSON wire contract for native IdentityRequest — produced by NativeIdentityRequestWire. */
final class NativeIdentityRequestFields {

    private NativeIdentityRequestFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String ITEMS = "items";
    static final String APPROVALS = "approvals";

    // request (parent) fields
    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String USER_FRIENDLY_TYPE = "userFriendlyType";
    static final String STATE = "state";
    static final String SOURCE = "source";
    static final String SOURCE_OBJECT = "sourceObject";
    static final String COMPLETION_STATUS = "completionStatus";
    static final String EXECUTION_STATUS = "executionStatus";
    static final String PRIORITY = "priority";
    static final String REQUESTER_ID = "requesterId";
    static final String REQUESTER_DISPLAY_NAME = "requesterDisplayName";
    static final String TARGET_ID = "targetId";
    static final String TARGET_DISPLAY_NAME = "targetDisplayName";
    static final String EXTERNAL_TICKET_ID = "externalTicketId";
    static final String PROCESS_ID = "processId";
    static final String TASK_RESULT_ID = "taskResultId";
    static final String EXECUTING = "executing";
    static final String FAILURE = "failure";
    static final String REJECTED = "rejected";
    static final String SUCCESSFUL = "successful";
    static final String TERMINATED = "terminated";
    static final String INCOMPLETE = "incomplete";
    static final String IIQ_ONLY = "iiqOnly";
    static final String PROVISIONING_COMPLETE = "provisioningComplete";
    static final String END_DATE = "endDate";
    static final String VERIFIED = "verified";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String ERRORS = "errors";
    static final String ITEM_COUNT = "itemCount";
    static final String APPROVAL_COUNT = "approvalCount";

    // lineage envelope
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";

    // item fields (nested in each request row)
    static final String ITEM_SOURCE_ID = "sourceId";
    static final String ITEM_REQUEST_SOURCE_ID = "requestSourceId";
    static final String ITEM_REQUEST_NAME = "requestName";
    static final String ITEM_APPLICATION = "application";
    static final String ITEM_ATTRIBUTE_NAME = "attributeName";
    static final String ITEM_ATTRIBUTE_VALUE = "attributeValue";
    static final String ITEM_OPERATION = "operation";
    static final String ITEM_MANAGED_ATTRIBUTE_TYPE = "managedAttributeType";
    static final String ITEM_ASSIGNMENT_ID = "assignmentId";
    static final String ITEM_NATIVE_IDENTITY = "nativeIdentity";
    static final String ITEM_INSTANCE = "instance";
    static final String ITEM_APPROVER_NAME = "approverName";
    static final String ITEM_APPROVAL_STATE = "approvalState";
    static final String ITEM_APPROVED = "approved";
    static final String ITEM_APPROVAL_COMPLETE = "approvalComplete";
    static final String ITEM_REJECTED = "rejected";
    static final String ITEM_PROVISIONING_STATE = "provisioningState";
    static final String ITEM_PROVISIONING_ENGINE = "provisioningEngine";
    static final String ITEM_PROVISIONING_REQUEST_ID = "provisioningRequestId";
    static final String ITEM_PROVISIONING_COMPLETE = "provisioningComplete";
    static final String ITEM_PROVISIONING_FAILED = "provisioningFailed";
    static final String ITEM_COMPILATION_STATUS = "compilationStatus";
    static final String ITEM_OWNER_NAME = "ownerName";
    static final String ITEM_REQUESTER_COMMENTS = "requesterComments";
    static final String ITEM_EXPANSION = "expansion";
    static final String ITEM_EXPANSION_CAUSE = "expansionCause";
    static final String ITEM_EXPANSION_INFO = "expansionInfo";
    static final String ITEM_RETRIES = "retries";
    static final String ITEM_IIQ = "iiq";
    static final String ITEM_START_DATE = "startDate";
    static final String ITEM_END_DATE = "endDate";
    static final String ITEM_CREATED = "created";
    static final String ITEM_MODIFIED = "modified";

    // approval fields (nested in each request row)
    static final String APPROVAL_REQUEST_SOURCE_ID = "requestSourceId";
    static final String APPROVAL_REQUEST_NAME = "requestName";
    static final String APPROVAL_WORK_ITEM_ID = "workItemId";
    static final String APPROVAL_WORK_ITEM_TYPE = "workItemType";
    static final String APPROVAL_OWNER = "owner";
    static final String APPROVAL_OWNER_ID = "ownerId";
    static final String APPROVAL_COMPLETER = "completer";
    static final String APPROVAL_APPROVED = "approved";
    static final String APPROVAL_STATE = "state";
    static final String APPROVAL_STATE_KEY = "stateKey";
    static final String APPROVAL_TYPE_KEY = "typeKey";
    static final String APPROVAL_START_DATE = "startDate";
    static final String APPROVAL_END_DATE = "endDate";
    static final String APPROVAL_ITEM_COUNT = "approvalItemCount";
    static final String APPROVAL_INDEX = "approvalIndex";
    static final String APPROVAL_COMMENTS = "comments";
    static final String APPROVAL_SIGN_OFF = "signOff";
}
