package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAccountRef;
import com.keyforge.nativeiiq.model.NativeIdentityRow;
import com.keyforge.nativeiiq.model.NativeRoleAssignmentRef;
import com.keyforge.nativeiiq.model.NativeRoleDetectionRef;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.Scope;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Maps a native {@code sailpoint.object.Identity} into a {@link NativeIdentityRow}. Read-only: it only
 * calls getters. Every relationship access is null-guarded so a sparsely-populated Identity never
 * throws. Uses only getters verified against the SailPoint IdentityIQ 8.4 Java API; exact signatures
 * are confirmed at compile time against the real {@code identityiq.jar} under the native Maven profile.
 *
 * <p>Preserves the native-rich projection (manager/administrator references, the full attribute map,
 * the assignment/detection role graph, capabilities and controlled scopes) rather than flattening to
 * the REST/SCIM shape.
 */
public final class NativeIdentityMapper {

    private NativeIdentityMapper() {
    }

    public static NativeIdentityRow map(Identity id, String sourceSystem, String extractionRunId) {
        NativeIdentityRow row = new NativeIdentityRow();

        row.setSourceId(id.getId());
        row.setName(id.getName());
        row.setDisplayName(id.getDisplayName());
        row.setDisplayableName(id.getDisplayableName());
        row.setFirstName(id.getFirstname());
        row.setLastName(id.getLastname());
        row.setEmail(id.getEmail());
        row.setInactive(Boolean.valueOf(id.isInactive()));
        row.setType(id.getType());
        row.setCorrelated(Boolean.valueOf(id.isCorrelated()));
        row.setManagerStatus(Boolean.valueOf(id.getManagerStatus()));

        // manager (native-only vs SCIM): the reference itself
        Identity mgr = id.getManager();
        if (mgr != null) {
            row.setManagerId(mgr.getId());
            row.setManagerName(mgr.getName());
        }

        // administrator (native-only reference)
        Identity admin = id.getAdministrator();
        if (admin != null) {
            row.setAdministratorId(admin.getId());
            row.setAdministratorName(admin.getName());
        }

        // accounts / links (application, native identity, instance, display name)
        List<Link> links = id.getLinks();
        if (links != null) {
            for (Link lk : links) {
                if (lk != null) {
                    row.getAccounts().add(new NativeAccountRef(
                            lk.getApplicationName(), lk.getNativeIdentity(),
                            lk.getInstance(), lk.getDisplayName()));
                }
            }
        }

        // assigned roles + detected roles (bundle names — the flat view)
        List<Bundle> assigned = id.getAssignedRoles();
        if (assigned != null) {
            for (Bundle b : assigned) {
                if (b != null) {
                    row.getAssignedRoles().add(b.getName());
                }
            }
        }
        List<Bundle> detected = id.getBundles();
        if (detected != null) {
            for (Bundle b : detected) {
                if (b != null) {
                    row.getDetectedRoles().add(b.getName());
                }
            }
        }

        // role graph (native-only richer view): assignments + detections
        List<RoleAssignment> roleAssignments = id.getRoleAssignments();
        if (roleAssignments != null) {
            for (RoleAssignment ra : roleAssignments) {
                if (ra != null) {
                    row.getRoleAssignments().add(new NativeRoleAssignmentRef(
                            ra.getRoleName(), ra.getRoleId(), ra.getComments()));
                }
            }
        }
        List<RoleDetection> roleDetections = id.getRoleDetections();
        if (roleDetections != null) {
            for (RoleDetection rd : roleDetections) {
                if (rd != null) {
                    row.getRoleDetections().add(new NativeRoleDetectionRef(
                            rd.getRoleName(), rd.getRoleId(), toInstant(rd.getDate()), rd.getAssignmentIds()));
                }
            }
        }

        // capabilities (native-only)
        List<Capability> capabilities = id.getCapabilities();
        if (capabilities != null) {
            for (Capability c : capabilities) {
                if (c != null) {
                    row.getCapabilities().add(c.getName());
                }
            }
        }

        // controlled scopes (native-only)
        List<Scope> scopes = id.getControlledScopes();
        if (scopes != null) {
            for (Scope s : scopes) {
                if (s != null) {
                    row.getControlledScopes().add(s.getName());
                }
            }
        }

        // full native attribute map — converted to JSON-safe values HERE, while the Identity is still
        // attached to its session (before the extractor decaches it). Raw values from getAttributes()
        // can be SailPoint objects / Hibernate proxies / enums / dates; JsonSafe degrades anything it
        // cannot represent to a safe string rather than letting a later serialize throw or a detached
        // proxy fail. One unusual attribute never fails the identity.
        Attributes<String, Object> attrs = id.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    row.getAttributes().put(key.toString(), JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        row.setCreated(toInstant(id.getCreated()));
        row.setModified(toInstant(id.getModified()));
        row.setLastRefresh(toInstant(id.getLastRefresh()));
        row.setLastLogin(toInstant(id.getLastLogin()));
        row.setScore(Integer.toString(id.getScore()));   // identity composite risk score

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
