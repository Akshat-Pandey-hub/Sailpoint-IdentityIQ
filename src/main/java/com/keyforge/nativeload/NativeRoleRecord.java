package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native Role (Bundle) row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_role}. Scalars held directly; nested structures carried as pre-serialized JSON
 * strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeRoleRecord {

    String sourceId;
    String name;
    String displayName;
    String displayableName;
    String fullName;
    String description;
    String type;
    String assignmentId;

    Boolean activityEnabled;
    Boolean allowDuplicateAccounts;
    Boolean allowMultipleAssignments;
    Boolean autoPromotion;
    Boolean differencable;
    Boolean iiqElevatedAccess;
    Boolean mergeTemplates;
    Boolean orProfiles;
    Boolean pendingDelete;
    Boolean hasSelector;
    Integer riskScoreWeight;

    String ownerId;
    String ownerName;
    Instant activationDate;
    Instant deactivationDate;

    // nested (jsonb)
    String descriptionsJson;
    String attributesJson;
    String roleTypeDefinition;
    String applicationsJson;
    String monitoredApplicationsJson;
    String scorecard;

    // timestamps
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

    public String getName() {
        return name;
    }
}
