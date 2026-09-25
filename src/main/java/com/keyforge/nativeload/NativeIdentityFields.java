package com.keyforge.nativeload;

/**
 * The JSON wire contract for the native Identity payload — the field names produced by the plugin
 * resource ({@code com.keyforge.nativeiiq.wire.NativeIdentityWire}) and consumed here. Kept in one
 * place so the loader never scatters string literals; keep in lock-step with the plugin-side wire.
 */
final class NativeIdentityFields {

    private NativeIdentityFields() {
    }

    // envelope
    static final String ROWS = "rows";
    static final String RETURNED = "returned";

    // scalars
    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String DISPLAY_NAME = "displayName";
    static final String DISPLAYABLE_NAME = "displayableName";
    static final String FIRST_NAME = "firstName";
    static final String LAST_NAME = "lastName";
    static final String EMAIL = "email";
    static final String INACTIVE = "inactive";
    static final String TYPE = "type";
    static final String CORRELATED = "correlated";
    static final String MANAGER_STATUS = "managerStatus";
    static final String MANAGER_ID = "managerId";
    static final String MANAGER_NAME = "managerName";
    static final String ADMINISTRATOR_ID = "administratorId";
    static final String ADMINISTRATOR_NAME = "administratorName";

    // collections / maps (persisted as jsonb)
    static final String ACCOUNTS = "accounts";
    static final String ASSIGNED_ROLES = "assignedRoles";
    static final String DETECTED_ROLES = "detectedRoles";
    static final String ROLE_ASSIGNMENTS = "roleAssignments";
    static final String ROLE_DETECTIONS = "roleDetections";
    static final String CAPABILITIES = "capabilities";
    static final String CONTROLLED_SCOPES = "controlledScopes";
    static final String ATTRIBUTES = "attributes";
    static final String SCORE = "score";

    // timestamps (ISO-8601 strings)
    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String LAST_REFRESH = "lastRefresh";
    static final String LAST_LOGIN = "lastLogin";

    // lineage
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
