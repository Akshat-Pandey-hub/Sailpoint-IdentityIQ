package com.keyforge.nativeload;

/** JSON wire contract for the native PolicyViolation payload. Keep in lock-step with the wire. */
final class NativeViolationFields {

    private NativeViolationFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String IDENTITY_ID = "identityId";
    static final String IDENTITY_NAME = "identityName";
    static final String POLICY_ID = "policyId";
    static final String POLICY_NAME = "policyName";
    static final String CONSTRAINT_ID = "constraintId";
    static final String CONSTRAINT_NAME = "constraintName";
    static final String STATUS = "status";
    static final String ACTIVE = "active";
    static final String LEFT_BUNDLES = "leftBundles";
    static final String RIGHT_BUNDLES = "rightBundles";
    static final String ENT_MARKED = "entitlementsMarkedForRemediation";
    static final String BUNDLES_MARKED = "bundlesMarkedForRemediation";
    static final String RELEVANT_APPS = "relevantApps";
    static final String VIOLATING_ENTITLEMENTS = "violatingEntitlements";
    static final String ARGUMENTS = "arguments";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
