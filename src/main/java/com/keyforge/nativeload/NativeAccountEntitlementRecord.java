package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native account-entitlement edge pulled from the plugin endpoint and prepared for upsert into
 * {@code iiq_native.kf_account_entitlement}. All fields scalar. Pure data holder.
 */
public final class NativeAccountEntitlementRecord {

    String linkId;
    String identityId;
    String identityName;
    String applicationId;
    String applicationName;
    String nativeIdentity;
    String instance;
    String attributeName;
    String attributeValue;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getLinkId() {
        return linkId;
    }

    public String getAttributeName() {
        return attributeName;
    }
}
