package com.keyforge.nativeload;

/**
 * JSON wire contract for the native CertificationArchive payload — field names produced by
 * {@code com.keyforge.nativeiiq.wire.NativeCertificationArchiveWire} and consumed here. Keep in lock-step.
 */
final class NativeCertificationArchiveFields {

    private NativeCertificationArchiveFields() {
    }

    static final String ROWS = "rows";
    static final String SOURCE_COUNT = "sourceCount";

    static final String SOURCE_ID = "sourceId";
    static final String NAME = "name";
    static final String CERTIFICATION_ID = "certificationId";
    static final String CERTIFICATION_GROUP_ID = "certificationGroupId";
    static final String CREATOR_NAME = "creatorName";
    static final String OWNER_NAME = "ownerName";
    static final String COMMENTS = "comments";

    static final String CHILD_CERTIFICATION_IDS = "childCertificationIds";
    static final String ARCHIVE_XML = "archiveXml";

    static final String SIGNED = "signed";
    static final String EXPIRATION = "expiration";
    static final String CREATED = "created";
    static final String MODIFIED = "modified";

    static final String SRC_SYSTEM = "srcSystem";
    static final String SRC_INTERFACE = "srcInterface";
    static final String SRC_OBJECT_TYPE = "srcObjectType";
    static final String SRC_NATURAL_KEY = "srcNaturalKey";
    static final String SRC_EVENT_TS = "srcEventTs";
    static final String EXTRACTION_RUN_ID = "extractionRunId";
    static final String EXTRACTED_AT = "extractedAt";
}
