package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeApplicationRow;
import com.keyforge.nativeiiq.model.NativeReferenceRef;
import com.keyforge.nativeiiq.model.NativeSchemaRef;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Schema;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps a native {@code sailpoint.object.Application} into a {@link NativeApplicationRow}. Read-only:
 * only getters. Every relationship is null-guarded. Uses only getters verified against the real 8.4
 * {@code identityiq.jar}. Nothing is inferred — a native {@code null} stays {@code null}.
 *
 * <p><b>Credential safety:</b> the connector config map ({@code getAttributes()}) can hold passwords
 * and secret keys. This mapper redacts every key the native object itself reports as encrypted/secret
 * ({@code getEncryptedAndSecretAttrList()} + {@code getEncrpytedConfigAttributes()}) to
 * {@code "<redacted>"} before persisting, and makes the remaining values JSON-safe while the object is
 * still attached to its session (before the extractor decaches).
 */
public final class NativeApplicationMapper {

    private static final String REDACTED = "<redacted>";

    private NativeApplicationMapper() {
    }

    public static NativeApplicationRow map(Application app, String sourceSystem, String extractionRunId) {
        NativeApplicationRow row = new NativeApplicationRow();

        row.setSourceId(app.getId());
        row.setName(app.getName());
        row.setDescription(app.getDescription());
        row.setType(app.getType());
        row.setConnector(app.getConnector());
        row.setFeaturesString(app.getFeaturesString());
        row.setProfileClass(app.getProfileClass());
        row.setProxiedName(app.getProxiedName());
        row.setCluster(app.getCluster());
        row.setIcon(app.getIcon());
        row.setAggregationTypes(app.getAggregationTypes());
        row.setBeforeProvisioningRule(app.getBeforeProvisioningRule());
        row.setAfterProvisioningRule(app.getAfterProvisioningRule());
        row.setScore(Integer.valueOf(app.getScore()));

        row.setAuthoritative(Boolean.valueOf(app.isAuthoritative()));
        row.setCaseInsensitive(Boolean.valueOf(app.isCaseInsensitive()));
        row.setLogical(Boolean.valueOf(app.isLogical()));
        row.setComposite(Boolean.valueOf(app.isComposite()));
        row.setAuthenticationResource(Boolean.valueOf(app.isAuthenticationResource()));
        row.setActivityEnabled(Boolean.valueOf(app.isActivityEnabled()));
        row.setInMaintenance(Boolean.valueOf(app.isInMaintenance()));
        row.setManagesOtherApps(Boolean.valueOf(app.isManagesOtherApps()));
        row.setNativeChangeDetectionEnabled(Boolean.valueOf(app.isNativeChangeDetectionEnabled()));
        row.setSupportsProvisioning(Boolean.valueOf(app.isSupportsProvisioning()));
        row.setSupportsAccountOnly(Boolean.valueOf(app.isSupportsAccountOnly()));
        row.setSupportsAdditionalAccounts(Boolean.valueOf(app.isSupportsAdditionalAccounts()));
        row.setSupportsAuthenticate(Boolean.valueOf(app.isSupportsAuthenticate()));
        row.setSupportsGroupProvisioning(Boolean.valueOf(app.isSupportsGroupProvisioning()));
        row.setSupportsDirectPermissions(Boolean.valueOf(app.isSupportsDirectPermissions()));
        row.setSyncProvisioning(Boolean.valueOf(app.isSyncProvisioning()));

        // owner (native reference)
        Identity owner = app.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }
        addIdentityRefs(app.getSecondaryOwners(), row.getSecondaryOwners());
        addIdentityRefs(app.getRemediators(), row.getRemediators());

        // dependencies (other Applications)
        List<Application> deps = app.getDependencies();
        if (deps != null) {
            for (Application dep : deps) {
                if (dep != null) {
                    row.getDependencies().add(new NativeReferenceRef(dep.getId(), dep.getName()));
                }
            }
        }

        // schemas (compact summary)
        List<Schema> schemas = app.getSchemas();
        if (schemas != null) {
            for (Schema s : schemas) {
                if (s != null) {
                    List<AttributeDefinition> defs = s.getAttributes();
                    row.getSchemas().add(new NativeSchemaRef(
                            s.getObjectType(), s.getNativeObjectType(), s.getIdentityAttribute(),
                            s.getDisplayAttribute(), s.getInstanceAttribute(),
                            defs == null ? Integer.valueOf(0) : Integer.valueOf(defs.size())));
                }
            }
        }

        // descriptions
        java.util.Map<String, String> descriptions = app.getDescriptions();
        if (descriptions != null) {
            for (java.util.Map.Entry<String, String> e : descriptions.entrySet()) {
                if (e.getKey() != null) {
                    row.getDescriptions().put(e.getKey(), e.getValue());
                }
            }
        }

        // connector config attributes — secrets redacted, remaining values JSON-safe
        Set<String> secret = secretKeys(app);
        Attributes<String, Object> attrs = app.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    String k = key.toString();
                    row.getAttributes().put(k, secret.contains(k) ? REDACTED : JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        row.setCreated(toInstant(app.getCreated()));
        row.setModified(toInstant(app.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void addIdentityRefs(List<Identity> identities, List<NativeReferenceRef> out) {
        if (identities == null) {
            return;
        }
        for (Identity id : identities) {
            if (id != null) {
                out.add(new NativeReferenceRef(id.getId(), id.getName()));
            }
        }
    }

    /** The set of config keys the native object reports as encrypted/secret — never persisted in clear. */
    private static Set<String> secretKeys(Application app) {
        Set<String> keys = new HashSet<String>();
        addAll(keys, app.getEncryptedAndSecretAttrList());
        addAll(keys, app.getEncrpytedConfigAttributes());
        return keys;
    }

    private static void addAll(Set<String> target, List<String> src) {
        if (src != null) {
            for (String s : src) {
                if (s != null) {
                    target.add(s);
                }
            }
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
