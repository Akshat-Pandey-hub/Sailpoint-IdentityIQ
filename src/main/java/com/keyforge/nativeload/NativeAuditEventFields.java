package com.keyforge.nativeload;

/** JSON wire contract for the native AuditEvent payload. Keep in lock-step with the wire. */
final class NativeAuditEventFields {

    private NativeAuditEventFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String SOURCE_ID = "sourceId";
    static final String ACTION = "action";
    static final String AUDIT_SOURCE = "auditSource";
    static final String TARGET = "target";
    static final String APPLICATION = "application";
    static final String ACCOUNT_NAME = "accountName";
    static final String INSTANCE = "instance";
    static final String ATTRIBUTE_NAME = "attributeName";
    static final String ATTRIBUTE_VALUE = "attributeValue";
    static final String INTERFACE_NAME = "interfaceName";
    static final String SERVER_HOST = "serverHost";
    static final String CLIENT_HOST = "clientHost";
    static final String TRACKING_ID = "trackingId";
    static final String STRING1 = "string1";
    static final String STRING2 = "string2";
    static final String STRING3 = "string3";
    static final String STRING4 = "string4";
    static final String ATTRIBUTES = "attributes";
    static final String CREATED = "created";
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
