package com.keyforge.nativeload;

/** JSON wire contract for native ProvisioningTransaction — produced by NativeProvisioningTxnWire. */
final class NativeProvisioningTxnFields {

    private NativeProvisioningTxnFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String ITEMS = "items";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String OPERATION = "operation";
    static final String TYPE = "type";
    static final String STATUS = "status";
    static final String SOURCE = "source";
    static final String INTEGRATION = "integration";
    static final String FORCED = "forced";
    static final String IDENTITY_NAME = "identityName";
    static final String IDENTITY_DISPLAY_NAME = "identityDisplayName";
    static final String APPLICATION_NAME = "applicationName";
    static final String NATIVE_IDENTITY = "nativeIdentity";
    static final String ACCOUNT_DISPLAY_NAME = "accountDisplayName";
    static final String CERTIFICATION_ID = "certificationId";
    static final String CERTIFICATION_NAME = "certificationName";
    static final String ACCESS_REQUEST_ID = "accessRequestId";
    static final String WAIT_WORK_ITEM_ID = "waitWorkItemId";
    static final String MANUAL_WORK_ITEM_ID = "manualWorkItemId";
    static final String TICKET_ID = "ticketId";
    static final String RETRY_REQUEST_ID = "retryRequestId";
    static final String LAST_RETRY = "lastRetry";
    static final String RETRY_COUNT = "retryCount";
    static final String TIMED_OUT = "timedOut";
    static final String FILTERED = "filtered";
    static final String PLAN_RESULT_STATUS = "planResultStatus";
    static final String PLAN_RESULT_REQUEST_ID = "planResultRequestId";
    static final String PLAN_RESULT_ERRORS = "planResultErrors";
    static final String ACCOUNT_REQUEST_OPERATION = "accountRequestOperation";
    static final String REQUEST_ID = "requestId";
    static final String ITEM_COUNT = "itemCount";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";

    // item fields (nested in each txn row)
    static final String ITEM_TXN_SOURCE_ID = "txnSourceId";
    static final String ITEM_IDENTITY_NAME = "identityName";
    static final String ITEM_TYPE = "itemType";
    static final String ITEM_OPERATION = "operation";
    static final String ITEM_APPLICATION_NAME = "applicationName";
    static final String ITEM_NATIVE_IDENTITY = "nativeIdentity";
    static final String ITEM_INSTANCE = "instance";
    static final String ITEM_ACCOUNT_OPERATION = "accountOperation";
    static final String ITEM_NAME = "name";
    static final String ITEM_VALUE = "value";
    static final String ITEM_VALUE_JSON = "valueJson";
    static final String ITEM_ASSIGNMENT_ID = "assignmentId";
    static final String ITEM_ASSIGNMENT = "assignment";
    static final String ITEM_PERMISSION_TARGET = "permissionTarget";
    static final String ITEM_PERMISSION_RIGHTS = "permissionRights";
    static final String ITEM_REQUEST_ID = "requestId";
    static final String ITEM_INDEX = "itemIndex";
}
