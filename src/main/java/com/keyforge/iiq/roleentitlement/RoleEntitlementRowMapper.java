package com.keyforge.iiq.roleentitlement;

import com.keyforge.iiq.model.Entitlement;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Maps a {@link RoleEntitlementGrant} to a {@link RoleEntitlementRow}. Pure and DB-free.
 * The application/property/value/displayValue come straight from the authoritative source;
 * {@code entitlement_id} is resolved against the already-extracted entitlement catalog by
 * (application, attribute, value), and left NULL when there is no matching catalog entry
 * (never fabricated). The primary key is deterministic so re-runs update in place.
 */
public final class RoleEntitlementRowMapper {

    private RoleEntitlementRowMapper() {
    }

    /**
     * @param catalogIndex from {@link #buildCatalogIndex(List)}; may be null (then entitlement_id is NULL)
     */
    public static RoleEntitlementRow map(RoleEntitlementGrant grant, String roleName, Map<String, String> catalogIndex) {
        String roleId = toCanonicalUuid(grant.roleId());
        String entitlementId = resolveEntitlementId(catalogIndex,
                grant.applicationName(), grant.property(), grant.value());
        String id = deterministicId(grant.roleId(), grant.applicationName(), grant.property(), grant.value());

        return new RoleEntitlementRow(
                id,
                roleId,
                blankToNull(roleName),
                blankToNull(grant.applicationName()),
                blankToNull(grant.property()),
                blankToNull(grant.value()),
                blankToNull(grant.displayValue()),
                blankToNull(grant.classifications()),
                entitlementId);
    }

    /** Index the entitlement catalog for (application, attribute, value) resolution. */
    public static Map<String, String> buildCatalogIndex(List<Entitlement> entitlements) {
        Map<String, String> index = new HashMap<>();
        if (entitlements == null) {
            return index;
        }
        for (Entitlement e : entitlements) {
            if (e.getId() == null) {
                continue;
            }
            String app = e.getApplication() == null ? null : e.getApplication().getDisplayName();
            String attr = e.getAttribute();
            String val = e.getValue();
            if (app == null || val == null) {
                continue;
            }
            index.putIfAbsent(key(app, attr, val), e.getId());
            index.putIfAbsent(key(app, null, val), e.getId()); // fallback: application + value
        }
        return index;
    }

    static String resolveEntitlementId(Map<String, String> index, String app, String property, String value) {
        if (index == null || app == null || value == null) {
            return null;
        }
        String id = index.get(key(app, property, value));
        if (id == null) {
            id = index.get(key(app, null, value));
        }
        return id == null ? null : canonicalOrNull(id);
    }

    private static String key(String app, String attr, String value) {
        return norm(app) + "|" + norm(attr) + "|" + norm(value);
    }

    static String deterministicId(String roleId, String applicationName, String property, String value) {
        String k = "RoleEntitlement|" + nullToEmpty(roleId) + "|" + nullToEmpty(applicationName)
                + "|" + nullToEmpty(property) + "|" + nullToEmpty(value);
        return UUID.nameUUIDFromBytes(k.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (RoleEntitlementMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new RoleEntitlementMappingException(
                    "IdentityIQ role id is missing; cannot form a kf_role_entitlement edge.");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new RoleEntitlementMappingException(
                    "IdentityIQ role id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
