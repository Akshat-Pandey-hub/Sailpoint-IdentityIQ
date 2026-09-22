package com.keyforge.nativeload;

/**
 * JSON wire contract for the native Link (account) payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeLinkWire} and consumed here. Keep in lock-step.
 */
final class NativeLinkFields {

    private NativeLinkFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String UUID = "uuid";
    static final String NATIVE_IDENTITY = "nativeIdentity";
    static final String DISPLAY_NAME = "displayName";
    static final String DISPLAYABLE_NAME = "displayableName";
    static final String INSTANCE = "instance";
    static final String COMPONENT_IDS = "componentIds";
    static final String APPLICATION_ID = "applicationId";
    static final String APPLICATION_NAME = "applicationName";
    static final String IDENTITY_ID = "identityId";
    static final String IDENTITY_NAME = "identityName";

    static final String DISABLED = "disabled";
    static final String LOCKED = "locked";
    static final String COMPOSITE = "composite";
    static final String MANUALLY_CORRELATED = "manuallyCorrelated";
    static final String HAS_ENTITLEMENTS = "hasEntitlements";
    static final String IIQ_DISABLED = "iiqDisabled";
    static final String IIQ_LOCKED = "iiqLocked";

    static final String PERMISSIONS = "permissions";
    static final String TARGET_PERMISSIONS = "targetPermissions";
    static final String ATTRIBUTES = "attributes";
    static final String ENTITLEMENT_ATTRIBUTES = "entitlementAttributes";

    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String LAST_REFRESH = "lastRefresh";
    static final String LAST_TARGET_AGGREGATION = "lastTargetAggregation";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
