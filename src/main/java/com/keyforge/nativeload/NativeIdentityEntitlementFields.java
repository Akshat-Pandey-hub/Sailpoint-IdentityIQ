package com.keyforge.nativeload;

/**
 * JSON wire contract for the native IdentityEntitlement payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeIdentityEntitlementWire} and consumed here. Keep in lock-step.
 */
final class NativeIdentityEntitlementFields {

    private NativeIdentityEntitlementFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";

    static final String SOURCE_ID = "sourceId";
    static final String IDENTITY_ID = "identityId";
    static final String IDENTITY_NAME = "identityName";
    static final String APPLICATION_ID = "applicationId";
    static final String APPLICATION_NAME = "applicationName";
    static final String NATIVE_IDENTITY = "nativeIdentity";
    static final String INSTANCE = "instance";
    static final String ATTRIBUTE_NAME = "attributeName";
    static final String ATTRIBUTE_VALUE = "attributeValue";
    static final String VALUE_LIST = "valueList";
    static final String TYPE = "type";
    static final String DISPLAY_NAME = "displayName";
    static final String ANNOTATION = "annotation";
    static final String ASSIGNED = "assigned";
    static final String GRANTED_BY_ROLE = "grantedByRole";
    static final String ALLOWED = "allowed";
    static final String CONNECTED = "connected";
    static final String AGGREGATION_STATE = "aggregationState";
    static final String SOURCE = "source";
    static final String SOURCE_OBJECT = "sourceObject";
    static final String ASSIGNER = "assigner";
    static final String ASSIGNMENT_ID = "assignmentId";
    static final String ASSIGNMENT_NOTE = "assignmentNote";
    static final String SOURCE_ASSIGNABLE_ROLES = "sourceAssignableRoles";
    static final String SOURCE_DETECTED_ROLES = "sourceDetectedRoles";
    static final String CERTIFICATION_ITEM_ID = "certificationItemId";
    static final String PENDING_CERTIFICATION_ITEM_ID = "pendingCertificationItemId";
    static final String REQUEST_ITEM_ID = "requestItemId";
    static final String PENDING_REQUEST_ITEM_ID = "pendingRequestItemId";
    static final String START_DATE = "startDate";
    static final String END_DATE = "endDate";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
