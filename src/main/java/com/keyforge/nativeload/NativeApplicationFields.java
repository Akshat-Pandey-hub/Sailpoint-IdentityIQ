package com.keyforge.nativeload;

/**
 * JSON wire contract for the native Application payload — the field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeApplicationWire} and consumed here. Keep in lock-step.
 */
final class NativeApplicationFields {

    private NativeApplicationFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String DESCRIPTION = "description";
    static final String TYPE = "type";
    static final String CONNECTOR = "connector";
    static final String FEATURES_STRING = "featuresString";
    static final String PROFILE_CLASS = "profileClass";
    static final String PROXIED_NAME = "proxiedName";
    static final String CLUSTER = "cluster";
    static final String ICON = "icon";
    static final String AGGREGATION_TYPES = "aggregationTypes";
    static final String BEFORE_PROVISIONING_RULE = "beforeProvisioningRule";
    static final String AFTER_PROVISIONING_RULE = "afterProvisioningRule";
    static final String SCORE = "score";

    static final String AUTHORITATIVE = "authoritative";
    static final String CASE_INSENSITIVE = "caseInsensitive";
    static final String LOGICAL = "logical";
    static final String COMPOSITE = "composite";
    static final String AUTHENTICATION_RESOURCE = "authenticationResource";
    static final String ACTIVITY_ENABLED = "activityEnabled";
    static final String IN_MAINTENANCE = "inMaintenance";
    static final String MANAGES_OTHER_APPS = "managesOtherApps";
    static final String NATIVE_CHANGE_DETECTION_ENABLED = "nativeChangeDetectionEnabled";
    static final String SUPPORTS_PROVISIONING = "supportsProvisioning";
    static final String SUPPORTS_ACCOUNT_ONLY = "supportsAccountOnly";
    static final String SUPPORTS_ADDITIONAL_ACCOUNTS = "supportsAdditionalAccounts";
    static final String SUPPORTS_AUTHENTICATE = "supportsAuthenticate";
    static final String SUPPORTS_GROUP_PROVISIONING = "supportsGroupProvisioning";
    static final String SUPPORTS_DIRECT_PERMISSIONS = "supportsDirectPermissions";
    static final String SYNC_PROVISIONING = "syncProvisioning";

    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String SECONDARY_OWNERS = "secondaryOwners";
    static final String REMEDIATORS = "remediators";
    static final String DEPENDENCIES = "dependencies";
    static final String SCHEMAS = "schemas";
    static final String DESCRIPTIONS = "descriptions";
    static final String ATTRIBUTES = "attributes";

    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
