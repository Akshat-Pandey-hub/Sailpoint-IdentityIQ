package com.keyforge.nativeload;

/**
 * JSON wire contract for the native Role (Bundle) payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeRoleWire} and consumed here. Keep in lock-step.
 */
final class NativeRoleFields {

    private NativeRoleFields() {
    }

    static final String ROWS = "rows";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String DISPLAY_NAME = "displayName";
    static final String DISPLAYABLE_NAME = "displayableName";
    static final String FULL_NAME = "fullName";
    static final String DESCRIPTION = "description";
    static final String TYPE = "type";
    static final String ASSIGNMENT_ID = "assignmentId";

    static final String ACTIVITY_ENABLED = "activityEnabled";
    static final String ALLOW_DUPLICATE_ACCOUNTS = "allowDuplicateAccounts";
    static final String ALLOW_MULTIPLE_ASSIGNMENTS = "allowMultipleAssignments";
    static final String AUTO_PROMOTION = "autoPromotion";
    static final String DIFFERENCABLE = "differencable";
    static final String IIQ_ELEVATED_ACCESS = "iiqElevatedAccess";
    static final String MERGE_TEMPLATES = "mergeTemplates";
    static final String OR_PROFILES = "orProfiles";
    static final String PENDING_DELETE = "pendingDelete";
    static final String HAS_SELECTOR = "hasSelector";
    static final String RISK_SCORE_WEIGHT = "riskScoreWeight";

    static final String OWNER_ID = "ownerId";
    static final String OWNER_NAME = "ownerName";
    static final String ACTIVATION_DATE = "activationDate";
    static final String DEACTIVATION_DATE = "deactivationDate";

    static final String DESCRIPTIONS = "descriptions";
    static final String ATTRIBUTES = "attributes";
    static final String ROLE_TYPE_DEFINITION = "roleTypeDefinition";
    static final String APPLICATIONS = "applications";
    static final String MONITORED_APPLICATIONS = "monitoredApplications";
    static final String SCORECARD = "scorecard";

    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
