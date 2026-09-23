package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code Application}, produced by the native Java-API layer.
 * Same business entity as the REST {@code kf_application}, but a SEPARATE model preserving native-only
 * fields. Pure data holder (no SailPoint dependency) so the mapper's output is unit-testable and no
 * Hibernate object escapes the extraction boundary.
 */
public final class NativeApplicationRow {

    // --- identity / core ---
    private String sourceId;
    private String name;
    private String description;
    private String type;
    private String connector;
    private String featuresString;
    private String profileClass;
    private String proxiedName;
    private String cluster;
    private String icon;
    private String aggregationTypes;
    private String beforeProvisioningRule;
    private String afterProvisioningRule;
    private Integer score;

    // --- account-schema + application rules (rule NAME references, source-exposed) ---
    private String accountSchemaCorrelationRule;
    private String accountSchemaCustomizationRule;
    private String accountSchemaCreationRule;
    private String accountSchemaRefreshRule;
    private String applicationCreationRule;
    private String accountSchemaCorrelationRuleId;
    private String accountSchemaCustomizationRuleId;
    private String accountSchemaCreationRuleId;
    private String accountSchemaRefreshRuleId;
    private String applicationCreationRuleId;

    // --- flags ---
    private Boolean authoritative;
    private Boolean caseInsensitive;
    private Boolean logical;
    private Boolean composite;
    private Boolean authenticationResource;
    private Boolean activityEnabled;
    private Boolean inMaintenance;
    private Boolean managesOtherApps;
    private Boolean nativeChangeDetectionEnabled;
    private Boolean supportsProvisioning;
    private Boolean supportsAccountOnly;
    private Boolean supportsAdditionalAccounts;
    private Boolean supportsAuthenticate;
    private Boolean supportsGroupProvisioning;
    private Boolean supportsDirectPermissions;
    private Boolean syncProvisioning;

    // --- owner + related identities (native references) ---
    private String ownerId;
    private String ownerName;
    private final List<NativeReferenceRef> secondaryOwners = new ArrayList<NativeReferenceRef>();
    private final List<NativeReferenceRef> remediators = new ArrayList<NativeReferenceRef>();
    private final List<NativeReferenceRef> dependencies = new ArrayList<NativeReferenceRef>();

    // --- schemas ---
    private final List<NativeSchemaRef> schemas = new ArrayList<NativeSchemaRef>();

    // --- descriptions + config attributes (secrets redacted, JSON-safe) ---
    private final Map<String, String> descriptions = new LinkedHashMap<String, String>();
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;
    private Instant modified;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Application";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getConnector() { return connector; }
    public void setConnector(String v) { this.connector = v; }

    public String getFeaturesString() { return featuresString; }
    public void setFeaturesString(String v) { this.featuresString = v; }

    public String getProfileClass() { return profileClass; }
    public void setProfileClass(String v) { this.profileClass = v; }

    public String getProxiedName() { return proxiedName; }
    public void setProxiedName(String v) { this.proxiedName = v; }

    public String getCluster() { return cluster; }
    public void setCluster(String v) { this.cluster = v; }

    public String getIcon() { return icon; }
    public void setIcon(String v) { this.icon = v; }

    public String getAggregationTypes() { return aggregationTypes; }
    public void setAggregationTypes(String v) { this.aggregationTypes = v; }

    public String getBeforeProvisioningRule() { return beforeProvisioningRule; }
    public void setBeforeProvisioningRule(String v) { this.beforeProvisioningRule = v; }

    public String getAfterProvisioningRule() { return afterProvisioningRule; }
    public void setAfterProvisioningRule(String v) { this.afterProvisioningRule = v; }

    public Integer getScore() { return score; }
    public void setScore(Integer v) { this.score = v; }

    public String getAccountSchemaCorrelationRule() { return accountSchemaCorrelationRule; }
    public void setAccountSchemaCorrelationRule(String v) { this.accountSchemaCorrelationRule = v; }

    public String getAccountSchemaCustomizationRule() { return accountSchemaCustomizationRule; }
    public void setAccountSchemaCustomizationRule(String v) { this.accountSchemaCustomizationRule = v; }

    public String getAccountSchemaCreationRule() { return accountSchemaCreationRule; }
    public void setAccountSchemaCreationRule(String v) { this.accountSchemaCreationRule = v; }

    public String getAccountSchemaRefreshRule() { return accountSchemaRefreshRule; }
    public void setAccountSchemaRefreshRule(String v) { this.accountSchemaRefreshRule = v; }

    public String getApplicationCreationRule() { return applicationCreationRule; }
    public void setApplicationCreationRule(String v) { this.applicationCreationRule = v; }
    public String getAccountSchemaCorrelationRuleId() { return accountSchemaCorrelationRuleId; }
    public void setAccountSchemaCorrelationRuleId(String v) { this.accountSchemaCorrelationRuleId = v; }
    public String getAccountSchemaCustomizationRuleId() { return accountSchemaCustomizationRuleId; }
    public void setAccountSchemaCustomizationRuleId(String v) { this.accountSchemaCustomizationRuleId = v; }
    public String getAccountSchemaCreationRuleId() { return accountSchemaCreationRuleId; }
    public void setAccountSchemaCreationRuleId(String v) { this.accountSchemaCreationRuleId = v; }
    public String getAccountSchemaRefreshRuleId() { return accountSchemaRefreshRuleId; }
    public void setAccountSchemaRefreshRuleId(String v) { this.accountSchemaRefreshRuleId = v; }
    public String getApplicationCreationRuleId() { return applicationCreationRuleId; }
    public void setApplicationCreationRuleId(String v) { this.applicationCreationRuleId = v; }

    public Boolean getAuthoritative() { return authoritative; }
    public void setAuthoritative(Boolean v) { this.authoritative = v; }

    public Boolean getCaseInsensitive() { return caseInsensitive; }
    public void setCaseInsensitive(Boolean v) { this.caseInsensitive = v; }

    public Boolean getLogical() { return logical; }
    public void setLogical(Boolean v) { this.logical = v; }

    public Boolean getComposite() { return composite; }
    public void setComposite(Boolean v) { this.composite = v; }

    public Boolean getAuthenticationResource() { return authenticationResource; }
    public void setAuthenticationResource(Boolean v) { this.authenticationResource = v; }

    public Boolean getActivityEnabled() { return activityEnabled; }
    public void setActivityEnabled(Boolean v) { this.activityEnabled = v; }

    public Boolean getInMaintenance() { return inMaintenance; }
    public void setInMaintenance(Boolean v) { this.inMaintenance = v; }

    public Boolean getManagesOtherApps() { return managesOtherApps; }
    public void setManagesOtherApps(Boolean v) { this.managesOtherApps = v; }

    public Boolean getNativeChangeDetectionEnabled() { return nativeChangeDetectionEnabled; }
    public void setNativeChangeDetectionEnabled(Boolean v) { this.nativeChangeDetectionEnabled = v; }

    public Boolean getSupportsProvisioning() { return supportsProvisioning; }
    public void setSupportsProvisioning(Boolean v) { this.supportsProvisioning = v; }

    public Boolean getSupportsAccountOnly() { return supportsAccountOnly; }
    public void setSupportsAccountOnly(Boolean v) { this.supportsAccountOnly = v; }

    public Boolean getSupportsAdditionalAccounts() { return supportsAdditionalAccounts; }
    public void setSupportsAdditionalAccounts(Boolean v) { this.supportsAdditionalAccounts = v; }

    public Boolean getSupportsAuthenticate() { return supportsAuthenticate; }
    public void setSupportsAuthenticate(Boolean v) { this.supportsAuthenticate = v; }

    public Boolean getSupportsGroupProvisioning() { return supportsGroupProvisioning; }
    public void setSupportsGroupProvisioning(Boolean v) { this.supportsGroupProvisioning = v; }

    public Boolean getSupportsDirectPermissions() { return supportsDirectPermissions; }
    public void setSupportsDirectPermissions(Boolean v) { this.supportsDirectPermissions = v; }

    public Boolean getSyncProvisioning() { return syncProvisioning; }
    public void setSyncProvisioning(Boolean v) { this.syncProvisioning = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public List<NativeReferenceRef> getSecondaryOwners() { return secondaryOwners; }
    public List<NativeReferenceRef> getRemediators() { return remediators; }
    public List<NativeReferenceRef> getDependencies() { return dependencies; }
    public List<NativeSchemaRef> getSchemas() { return schemas; }
    public Map<String, String> getDescriptions() { return descriptions; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }

    public String getSrcInterface() { return srcInterface; }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }

    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }

    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
