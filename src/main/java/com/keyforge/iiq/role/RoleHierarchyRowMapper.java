package com.keyforge.iiq.role;

import com.keyforge.iiq.model.Role;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Derives {@code kf_role_hierarchy} edges from a {@link Role}'s verified role→role arrays:
 * {@code inheritance} → {@code inherits}, {@code requirements} → {@code requires},
 * {@code permits} → {@code permits}. Pure and DB-free.
 *
 * <p>On the current instance all three arrays are empty, so this correctly yields ZERO
 * rows — that is expected, not a failure. Nothing is invented: an edge is emitted only
 * when the source array contains a reference with a usable id.
 */
public final class RoleHierarchyRowMapper {

    static final String EDGE_INHERITS = "inherits";
    static final String EDGE_REQUIRES = "requires";
    static final String EDGE_PERMITS = "permits";

    private RoleHierarchyRowMapper() {
    }

    /** All hierarchy edges for one role (may be empty; never null). */
    public static List<RoleHierarchyRow> mapAll(Role role) {
        List<RoleHierarchyRow> rows = new ArrayList<>();
        String roleId = canonicalOrNull(role.getId());
        if (roleId == null) {
            // A role whose own id is not a UUID cannot anchor edges; skip (reported by caller path).
            return rows;
        }
        String roleName = blankToNull(role.getDisplayableName()) != null
                ? role.getDisplayableName() : role.getName();

        addEdges(rows, roleId, roleName, role.getInheritance(), EDGE_INHERITS);
        addEdges(rows, roleId, roleName, role.getRequirements(), EDGE_REQUIRES);
        addEdges(rows, roleId, roleName, role.getPermits(), EDGE_PERMITS);
        return rows;
    }

    private static void addEdges(List<RoleHierarchyRow> rows, String roleId, String roleName,
                                 List<Role.Ref> refs, String edgeType) {
        if (refs == null) {
            return;
        }
        for (Role.Ref ref : refs) {
            String relatedRoleId = ref == null ? null : canonicalOrNull(ref.getValue());
            if (relatedRoleId == null) {
                // No usable target id -> cannot form a real edge; never fabricate one.
                continue;
            }
            String relatedDisplay = ref.getDisplayName();
            rows.add(new RoleHierarchyRow(
                    deterministicId(roleId, edgeType, relatedRoleId),
                    roleId,
                    roleName,
                    relatedRoleId,
                    blankToNull(relatedDisplay),
                    edgeType));
        }
    }

    static String deterministicId(String roleId, String edgeType, String relatedRoleId) {
        String key = "RoleHierarchy|" + roleId + "|" + edgeType + "|" + relatedRoleId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Canonical-UUID helper (null on failure) — self-contained per the per-vertical convention.
    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        try {
            if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
                String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                        + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
                return UUID.fromString(dashed).toString();
            }
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
