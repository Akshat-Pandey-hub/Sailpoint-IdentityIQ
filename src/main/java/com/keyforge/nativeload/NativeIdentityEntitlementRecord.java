package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native IdentityEntitlement row pulled from the plugin endpoint and prepared for upsert into
 * {@code iiq_native.kf_identity_entitlement}. Scalars held directly; {@code valueList} carried as a
 * pre-serialized JSON string for a {@code jsonb} column. Pure data holder.
 */
public final class NativeIdentityEntitlementRecord {

    String sourceId;
    String identityId;
    String identityName;
    String applicationId;
    String applicationName;
    String nativeIdentity;
    String instance;
    String attributeName;
    String attributeValue;
    String valueListJson;
    String type;
    String displayName;
    String annotation;
    Boolean assigned;
    Boolean grantedByRole;
    Boolean allowed;
    Boolean connected;
    String aggregationState;
    String source;
    String sourceObject;
    String assigner;
    String assignmentId;
    String assignmentNote;
    String sourceAssignableRoles;
    String sourceDetectedRoles;
    String certificationItemId;
    String pendingCertificationItemId;
    String requestItemId;
    String pendingRequestItemId;
    Instant startDate;
    Instant endDate;
    Instant created;
    Instant modified;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getIdentityName() {
        return identityName;
    }
}
