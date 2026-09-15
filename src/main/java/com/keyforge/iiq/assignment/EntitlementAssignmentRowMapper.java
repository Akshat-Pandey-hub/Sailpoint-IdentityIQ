package com.keyforge.iiq.assignment;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Maps a derived {@link AccountEntitlementAssignment} to an
 * {@link EntitlementAssignmentRow}. Pure and DB-free. The account/entitlement
 * relationships become resolved FK columns; the remaining derivation provenance is
 * stored in typed columns (never a raw JSON dump). No value is invented.
 */
public final class EntitlementAssignmentRowMapper {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private EntitlementAssignmentRowMapper() {
    }

    public static EntitlementAssignmentRow map(AccountEntitlementAssignment assignment,
                                               Set<String> existingAccountIds,
                                               Set<String> existingEntitlementIds) {
        String assignmentid = deterministicAssignmentId(assignment);
        String accountid = resolveId(assignment.getAccountId(), existingAccountIds);
        String entitlementid = resolveId(assignment.getEntitlementId(), existingEntitlementIds);

        return new EntitlementAssignmentRow(
                assignmentid,
                accountid,
                entitlementid,
                blankToNull(assignment.getAccountName()),
                canonicalOrNull(assignment.getIdentityId()),
                blankToNull(assignment.getIdentityDisplayName()),
                canonicalOrNull(assignment.getApplicationId()),
                blankToNull(assignment.getApplicationName()),
                blankToNull(assignment.getEntitlementValue()),
                blankToNull(assignment.getEntitlementType()),
                blankToNull(assignment.getSourceAttribute()),
                assignment.getResolutionStatus() == null ? null : assignment.getResolutionStatus().name(),
                resolutionSourcesJson(assignment.getResolutionSources()));
    }

    /**
     * Deterministic, stable primary key from the derivation's logical key —
     * {@code accountId | applicationId | sourceAttribute | entitlementValue}.
     */
    public static String deterministicAssignmentId(AccountEntitlementAssignment assignment) {
        String key = nullToEmpty(assignment.getAccountId()) + "|"
                + nullToEmpty(assignment.getApplicationId()) + "|"
                + nullToEmpty(assignment.getSourceAttribute()) + "|"
                + nullToEmpty(assignment.getEntitlementValue());
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Canonical UUID iff it exists in the migration set, else null (never fabricated). */
    public static String resolveId(String rawId, Set<String> existingIds) {
        String canonical = canonicalOrNull(rawId);
        return canonical != null && existingIds != null && existingIds.contains(canonical) ? canonical : null;
    }

    private static String resolutionSourcesJson(Set<ResolutionSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        ArrayNode arr = JSON.arrayNode();
        for (ResolutionSource s : sources) {
            arr.add(s.name());
        }
        return arr.toString();
    }

    /** Canonical UUID or null; never throws. */
    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (EntitlementAssignmentMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
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
            throw new EntitlementAssignmentMappingException("'" + rawId + "' is not a valid PostgreSQL UUID");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
