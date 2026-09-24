package com.keyforge.nativeload;

/** JSON wire contract for the native policy-constraint payload. Keep in lock-step with the wire. */
final class NativePolicyConstraintFields {

    private NativePolicyConstraintFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String RETURNED_POLICIES = "returnedPolicies";

    static final String SOURCE_ID = "sourceId";
    static final String POLICY_ID = "policyId";
    static final String POLICY_NAME = "policyName";
    static final String NAME = "name";
    static final String DESCRIPTION = "description";
    static final String CONSTRAINT_TYPE = "constraintType";
    static final String WEIGHT = "weight";
    static final String COMPENSATING_CONTROL = "compensatingControl";
    static final String VIOLATION_OWNER_ID = "violationOwnerId";
    static final String VIOLATION_OWNER_NAME = "violationOwnerName";
    static final String VIOLATION_OWNER_TYPE = "violationOwnerType";
    static final String LEFT_BUNDLES = "leftBundles";
    static final String RIGHT_BUNDLES = "rightBundles";
    static final String SELECTORS = "selectors";
    static final String SELECTOR_COUNT = "selectorCount";
    static final String ARGUMENTS = "arguments";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
