package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeIdentityRoleRow;

import sailpoint.object.Identity;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expands an {@code Identity} into its role edges: one ASSIGNED edge per {@code RoleAssignment} (with the
 * stable {@code assignmentId}, comments, targets, future/soft-permit flags) and one DETECTED edge per
 * {@code RoleDetection} (with the detection's assignment-id evidence + date). Read-only, verified getters
 * only, nothing inferred. No assigner/date is fabricated for RoleAssignment (not exposed by the 8.4 API).
 */
public final class NativeIdentityRoleMapper {

    private NativeIdentityRoleMapper() {
    }

    /** Appends every role edge of {@code identity} to {@code out}. Returns the number appended. */
    public static int mapInto(Identity identity, List<NativeIdentityRoleRow> out,
                              String sourceSystem, String extractionRunId) {
        String identityId = identity.getId();
        String identityName = identity.getName();
        int count = 0;

        List<RoleAssignment> assignments = identity.getRoleAssignments();
        if (assignments != null) {
            for (RoleAssignment ra : assignments) {
                if (ra == null || ra.getRoleId() == null) {
                    continue;
                }
                NativeIdentityRoleRow row = base(identityId, identityName, sourceSystem, extractionRunId);
                row.setRelationshipType("ASSIGNED");
                row.setRoleId(ra.getRoleId());
                row.setRoleName(ra.getRoleName());
                row.setAssignmentId(ra.getId());
                row.setComments(ra.getComments());
                row.setFutureAssignment(Boolean.valueOf(ra.isFutureAssignment()));
                row.setPromotedSoftPermit(Boolean.valueOf(ra.isPromotedSoftPermit()));
                List<RoleTarget> targets = ra.getTargets();
                if (targets != null) {
                    for (RoleTarget t : targets) {
                        if (t != null) {
                            row.getTargets().add(target(t));
                        }
                    }
                }
                out.add(row);
                count++;
            }
        }

        List<RoleDetection> detections = identity.getRoleDetections();
        if (detections != null) {
            for (RoleDetection rd : detections) {
                if (rd == null || rd.getRoleId() == null) {
                    continue;
                }
                NativeIdentityRoleRow row = base(identityId, identityName, sourceSystem, extractionRunId);
                row.setRelationshipType("DETECTED");
                row.setRoleId(rd.getRoleId());
                row.setRoleName(rd.getRoleName());
                row.setDetectionAssignmentIds(rd.getAssignmentIds());
                row.setDetectionDate(toInstant(rd.getDate()));
                out.add(row);
                count++;
            }
        }
        return count;
    }

    private static NativeIdentityRoleRow base(String identityId, String identityName,
                                              String sourceSystem, String extractionRunId) {
        NativeIdentityRoleRow row = new NativeIdentityRoleRow();
        row.setIdentityId(identityId);
        row.setIdentityName(identityName);
        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Map<String, Object> target(RoleTarget t) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("applicationId", t.getApplicationId());
        m.put("applicationName", t.getApplicationName());
        m.put("instance", t.getInstance());
        m.put("nativeIdentity", t.getNativeIdentity());
        m.put("roleName", t.getRoleName());
        return m;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
