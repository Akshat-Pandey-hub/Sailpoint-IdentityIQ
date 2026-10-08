package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of one account&nbsp;&harr;&nbsp;entitlement edge, derived from a SailPoint
 * {@code Link}. This is the <b>account-side aggregated truth</b> — a value currently present on the account —
 * distinct from {@code IdentityEntitlement} (IIQ's tracked/assigned record). The {@code type} marker names
 * the source of the edge:
 * <ul>
 *   <li>{@code ATTRIBUTE} — one per (link, attribute name, value) from {@code Link.getEntitlementAttributes()}
 *       (schema attributes flagged as entitlements); {@code attributeName}/{@code attributeValue} populated.</li>
 *   <li>{@code PERMISSION} — one per {@code sailpoint.object.Permission} from {@code Link.getPermissions()}
 *       (directly-aggregated permissions); the {@code permission*} fields populated.</li>
 *   <li>{@code TARGET_PERMISSION} — one per Permission from {@code Link.getTargetPermissions()}
 *       (target/unstructured permissions); the {@code permission*} fields populated.</li>
 * </ul>
 * Pure data holder.
 */
public final class NativeAccountEntitlementRow {

    private String linkId;
    private String identityId;
    private String identityName;
    private String applicationId;
    private String applicationName;
    private String nativeIdentity;
    private String instance;
    private String type;               // ATTRIBUTE | PERMISSION | TARGET_PERMISSION
    private String attributeName;
    private String attributeValue;
    private String permissionTarget;        // Permission.getTarget()
    private String permissionRights;        // Permission.getRights() (comma-joined)
    private final List<String> permissionRightsList = new ArrayList<String>(); // Permission.getRightsList()
    private String permissionAnnotation;    // Permission.getAnnotation()
    private String permissionAggregationSource; // Permission.getAggregationSource()
    private final Map<String, Object> permissionAttributes = new LinkedHashMap<String, Object>(); // Permission.getAttributes()

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Link.entitlementAttributes";
    private String extractionRunId;
    private Instant extractedAt;

    public String getLinkId() { return linkId; }
    public void setLinkId(String v) { this.linkId = v; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String v) { this.identityId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }
    public String getPermissionTarget() { return permissionTarget; }
    public void setPermissionTarget(String v) { this.permissionTarget = v; }
    public String getPermissionRights() { return permissionRights; }
    public void setPermissionRights(String v) { this.permissionRights = v; }
    public List<String> getPermissionRightsList() { return permissionRightsList; }
    public String getPermissionAnnotation() { return permissionAnnotation; }
    public void setPermissionAnnotation(String v) { this.permissionAnnotation = v; }
    public String getPermissionAggregationSource() { return permissionAggregationSource; }
    public void setPermissionAggregationSource(String v) { this.permissionAggregationSource = v; }
    public Map<String, Object> getPermissionAttributes() { return permissionAttributes; }

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
