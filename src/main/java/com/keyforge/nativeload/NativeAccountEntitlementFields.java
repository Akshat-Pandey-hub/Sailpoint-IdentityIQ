package com.keyforge.nativeload;

/**
 * JSON wire contract for the native account-entitlement payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeAccountEntitlementWire} and consumed here. Keep in lock-step.
 */
final class NativeAccountEntitlementFields {

    private NativeAccountEntitlementFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String RETURNED_LINKS = "returnedLinks";

    static final String LINK_ID = "linkId";
    static final String IDENTITY_ID = "identityId";
    static final String IDENTITY_NAME = "identityName";
    static final String APPLICATION_ID = "applicationId";
    static final String APPLICATION_NAME = "applicationName";
    static final String NATIVE_IDENTITY = "nativeIdentity";
    static final String INSTANCE = "instance";
    static final String ATTRIBUTE_NAME = "attributeName";
    static final String ATTRIBUTE_VALUE = "attributeValue";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
