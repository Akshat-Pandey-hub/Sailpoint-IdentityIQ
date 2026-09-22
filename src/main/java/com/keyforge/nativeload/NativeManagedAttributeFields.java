package com.keyforge.nativeload;

/**
 * JSON wire contract for the native ManagedAttribute payload — the field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeManagedAttributeWire} and consumed here. Keep in lock-step.
 */
final class NativeManagedAttributeFields {

    private NativeManagedAttributeFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String VALUE = "value";
    static final String DISPLAY_NAME = "displayName";
    static final String DISPLAYABLE_NAME = "displayableName";
    static final String ATTRIBUTE = "attribute";
    static final String TYPE = "type";
    static final String UUID = "uuid";
    static final String REFERENCE_ATTRIBUTE = "referenceAttribute";
    static final String PURVIEW = "purview";
    static final String APPLICATION_ID = "applicationId";
    static final String APPLICATION_NAME = "applicationName";
    static final String INSTANCE = "instance";
    static final String NATIVE_IDENTITY = "nativeIdentity";
    static final String REQUESTABLE = "requestable";
    static final String GROUP = "group";
    static final String PERMISSION = "permission";
    static final String UNCORRELATED = "uncorrelated";
    static final String AGGREGATED = "aggregated";
    static final String IIQ_ELEVATED_ACCESS = "iiqElevatedAccess";
    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String DESCRIPTION = "description";
    static final String DESCRIPTIONS = "descriptions";
    static final String PERMISSIONS = "permissions";
    static final String TARGET_PERMISSIONS = "targetPermissions";
    static final String INHERITANCE = "inheritance";
    static final String ASSOCIATIONS = "associations";
    static final String ATTRIBUTES = "attributes";
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
