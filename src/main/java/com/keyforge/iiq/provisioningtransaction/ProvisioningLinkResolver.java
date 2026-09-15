package com.keyforge.iiq.provisioningtransaction;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic, conservative resolution of a ProvisioningTransaction's own authoritative
 * references. Pure and DB-free.
 *
 * <ul>
 *   <li><b>Request</b> ({@code accessRequestId}) → resolved against the persisted
 *       {@code kf_access_request} table by the union of its {@code request_number} and
 *       {@code requestid} keys, requiring exactly one distinct match (0 → UNRESOLVED, &gt;1 →
 *       AMBIGUOUS). The raw {@code accessRequestId} is always preserved by the caller.</li>
 *   <li><b>Certification</b> ({@code certificationName}) → certifications are not extracted by this
 *       tool (out of scope), and the transaction exposes only a certification <i>name</i>, not a
 *       certification-decision id. Such a reference is therefore always UNRESOLVED with the raw name
 *       preserved — never resolved to a fabricated decision id.</li>
 * </ul>
 */
public final class ProvisioningLinkResolver {

    public enum Status { RESOLVED, UNRESOLVED, AMBIGUOUS }

    public record Result(Status status, String targetType, String targetId, String rule) {
    }

    private final Map<String, Set<String>> requestByKey;

    public ProvisioningLinkResolver(Map<String, Set<String>> requestByKey) {
        this.requestByKey = requestByKey == null ? Map.of() : requestByKey;
    }

    /** Resolve a non-blank {@code accessRequestId} to a single {@code kf_access_request}. */
    public Result resolveRequest(String accessRequestId) {
        String key = accessRequestId == null ? "" : accessRequestId.trim();
        if (key.isEmpty()) {
            return new Result(Status.UNRESOLVED, null, null, "empty accessRequestId");
        }
        Set<String> ids = requestByKey.get(key);
        if (ids == null || ids.isEmpty()) {
            return new Result(Status.UNRESOLVED, null, null,
                    "no AccessRequest match on kf_access_request.request_number|requestid");
        }
        if (ids.size() > 1) {
            return new Result(Status.AMBIGUOUS, null, null,
                    "ambiguous: " + ids.size() + " AccessRequest matches");
        }
        return new Result(Status.RESOLVED, "AccessRequest", ids.iterator().next(),
                "kf_access_request.request_number|requestid");
    }
}
