package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native Identity row as pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_identity}. Scalars are held directly; nested structures (accounts, role
 * graph, capabilities, scopes, attributes) are carried as pre-serialized JSON strings destined for
 * {@code jsonb} columns. Pure data holder.
 */
public final class NativeIdentityRecord {

    // identity + scalars
    String sourceId;
    String name;
    String displayName;
    String displayableName;
    String firstName;
    String lastName;
    String email;
    Boolean inactive;
    String type;
    Boolean correlated;
    Boolean managerStatus;
    String managerId;
    String managerName;
    String administratorId;
    String administratorName;

    // nested (jsonb)
    String accountsJson;
    String assignedRolesJson;
    String detectedRolesJson;
    String roleAssignmentsJson;
    String roleDetectionsJson;
    String capabilitiesJson;
    String controlledScopesJson;
    String attributesJson;

    // timestamps
    Instant created;
    Instant modified;
    Instant lastRefresh;
    Instant lastLogin;

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
