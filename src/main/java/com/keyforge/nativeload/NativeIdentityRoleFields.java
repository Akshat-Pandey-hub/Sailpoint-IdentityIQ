package com.keyforge.nativeload;

/** JSON wire contract for native identity-role edges — produced by NativeIdentityRoleWire. */
final class NativeIdentityRoleFields {

    private NativeIdentityRoleFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";
    static final String RETURNED_IDENTITIES = "returnedIdentities";

    static final String IDENTITY_ID = "identityId";
    static final String IDENTITY_NAME = "identityName";
    static final String ROLE_ID = "roleId";
    static final String ROLE_NAME = "roleName";
    static final String RELATIONSHIP_TYPE = "relationshipType";
    static final String ASSIGNMENT_ID = "assignmentId";
    static final String DETECTION_ASSIGNMENT_IDS = "detectionAssignmentIds";
    static final String COMMENTS = "comments";
    static final String FUTURE_ASSIGNMENT = "futureAssignment";
    static final String PROMOTED_SOFT_PERMIT = "promotedSoftPermit";
    static final String DETECTION_DATE = "detectionDate";
    static final String TARGETS = "targets";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
