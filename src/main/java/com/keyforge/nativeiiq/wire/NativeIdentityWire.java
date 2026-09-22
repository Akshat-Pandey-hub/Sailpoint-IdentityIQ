package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeAccountRef;
import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRow;
import com.keyforge.nativeiiq.model.NativeRoleAssignmentRef;
import com.keyforge.nativeiiq.model.NativeRoleDetectionRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeExtractionResult} into a plain JSON-friendly structure (only Maps, Lists,
 * Strings, Booleans and Numbers) so it serializes deterministically with IIQ's {@code JsonHelper}
 * regardless of the runtime's Jackson date configuration. Timestamps are emitted as ISO-8601 strings.
 *
 * <p>Pure Java (no SailPoint dependency) but lives under {@code com.keyforge.nativeiiq.*} so it is
 * bundled in the plugin jar. The field names here are the wire contract consumed by the standalone
 * loader ({@code com.keyforge.nativeload.NativeIdentityFields}) — keep the two in lock-step.
 */
public final class NativeIdentityWire {

    private NativeIdentityWire() {
    }

    /**
     * Builds a small, always-serializable error envelope
     * ({@code {entity, status, error:{type, message}}}) for any failure — including a
     * {@link java.lang.Error}. Contains no identity data. Pure and null-tolerant.
     */
    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Identity");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    /**
     * Builds the response envelope:
     * {@code {entity, sourceSystem, extractionRunId, start, limit, returned, rows:[...]}}.
     */
    public static Map<String, Object> envelope(NativeExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeIdentityRow r : result.getIdentities()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Identity");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeIdentityRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("displayName", r.getDisplayName());
        m.put("displayableName", r.getDisplayableName());
        m.put("firstName", r.getFirstName());
        m.put("lastName", r.getLastName());
        m.put("email", r.getEmail());
        m.put("inactive", r.getInactive());
        m.put("type", r.getType());
        m.put("correlated", r.getCorrelated());
        m.put("managerStatus", r.getManagerStatus());
        m.put("managerId", r.getManagerId());
        m.put("managerName", r.getManagerName());
        m.put("administratorId", r.getAdministratorId());
        m.put("administratorName", r.getAdministratorName());

        List<Map<String, Object>> accounts = new ArrayList<Map<String, Object>>();
        for (NativeAccountRef a : r.getAccounts()) {
            Map<String, Object> am = new LinkedHashMap<String, Object>();
            am.put("applicationName", a.getApplicationName());
            am.put("nativeIdentity", a.getNativeIdentity());
            am.put("instance", a.getInstance());
            am.put("displayName", a.getDisplayName());
            accounts.add(am);
        }
        m.put("accounts", accounts);

        m.put("assignedRoles", new ArrayList<String>(r.getAssignedRoles()));
        m.put("detectedRoles", new ArrayList<String>(r.getDetectedRoles()));

        List<Map<String, Object>> ras = new ArrayList<Map<String, Object>>();
        for (NativeRoleAssignmentRef ra : r.getRoleAssignments()) {
            Map<String, Object> rm = new LinkedHashMap<String, Object>();
            rm.put("roleName", ra.getRoleName());
            rm.put("roleId", ra.getRoleId());
            rm.put("comments", ra.getComments());
            ras.add(rm);
        }
        m.put("roleAssignments", ras);

        List<Map<String, Object>> rds = new ArrayList<Map<String, Object>>();
        for (NativeRoleDetectionRef rd : r.getRoleDetections()) {
            Map<String, Object> rm = new LinkedHashMap<String, Object>();
            rm.put("roleName", rd.getRoleName());
            rm.put("roleId", rd.getRoleId());
            rm.put("date", iso(rd.getDate()));
            rm.put("assignmentIds", rd.getAssignmentIds());
            rds.add(rm);
        }
        m.put("roleDetections", rds);

        m.put("capabilities", new ArrayList<String>(r.getCapabilities()));
        m.put("controlledScopes", new ArrayList<String>(r.getControlledScopes()));
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));

        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("lastRefresh", iso(r.getLastRefresh()));
        m.put("lastLogin", iso(r.getLastLogin()));

        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
