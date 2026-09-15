package com.keyforge.iiq.accessrequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps an {@link AccessRequest} (and its items/approvals) to the three {@code kf_*} rows.
 * Pure and DB-free. Epoch-millis timestamps become UTC {@code LocalDateTime}; ids are
 * canonicalised; approval rows get a deterministic PK (interactions carry no id). Nothing invented.
 */
public final class AccessRequestRowMapper {

    private AccessRequestRowMapper() {
    }

    public static AccessRequestRow mapRequest(AccessRequest r) {
        return new AccessRequestRow(
                toCanonicalUuid(r.id()),
                blankToNull(r.requestId()),
                blankToNull(r.type()),
                blankToNull(r.requesterDisplayName()),
                blankToNull(r.targetDisplayName()),
                blankToNull(r.state()),
                blankToNull(r.executionStatus()),
                blankToNull(r.completionStatus()),
                blankToNull(r.priority()),
                blankToNull(r.externalTicketId()),
                r.cancelable(),
                epochToUtc(r.createdDate()),
                epochToUtc(r.endDate()),
                epochToUtc(r.terminatedDate()),
                epochToUtc(r.verificationDate()),
                r.items() == null ? 0 : r.items().size());
    }

    public static RequestItemRow mapItem(AccessRequest r, AccessRequest.Item it) {
        return new RequestItemRow(
                toCanonicalUuid(it.id()),
                toCanonicalUuid(r.id()),
                blankToNull(r.requestId()),
                blankToNull(it.operation()),
                blankToNull(it.applicationName()),
                blankToNull(it.accountName()),
                blankToNull(it.displayableAccountName()),
                blankToNull(it.instance()),
                blankToNull(it.name()),
                blankToNull(it.value()),
                blankToNull(it.displayableValue()),
                it.role(),
                it.entitlement(),
                it.hasManagedAttribute(),
                blankToNull(it.approvalState()),
                blankToNull(it.provisioningState()),
                blankToNull(it.provisioningEngine()),
                blankToNull(it.assignmentId()),
                it.retries(),
                blankToNull(it.requesterComments()),
                epochToUtc(it.startDate()),
                epochToUtc(it.endDate()),
                blankToNull(it.provisioningRequestId()));
    }

    public static RequestApprovalRow mapApproval(AccessRequest r, AccessRequest.Approval a, int index) {
        String requestid = toCanonicalUuid(r.id());
        String id = deterministicApprovalId(requestid, index, a.openDate(), a.ownerDisplayName());
        return new RequestApprovalRow(
                id,
                requestid,
                blankToNull(r.requestId()),
                blankToNull(a.ownerDisplayName()),
                blankToNull(a.status()),
                blankToNull(a.description()),
                blankToNull(a.comments()),
                a.approvalItemCount(),
                canonicalOrNull(a.workItemId()),
                blankToNull(a.workItemName()),
                blankToNull(a.workItemArchiveId()),
                epochToUtc(a.openDate()),
                epochToUtc(a.completeDate()));
    }

    static String deterministicApprovalId(String requestid, int index, Long openDate, String owner) {
        String key = "RequestApproval|" + requestid + "|" + index + "|" + openDate + "|" + nullToEmpty(owner);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static LocalDateTime epochToUtc(Long epochMillis) {
        return epochMillis == null ? null
                : Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (AccessRequestMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new AccessRequestMappingException("IdentityIQ id is missing; cannot form a UUID key.");
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
            throw new AccessRequestMappingException("IdentityIQ id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
