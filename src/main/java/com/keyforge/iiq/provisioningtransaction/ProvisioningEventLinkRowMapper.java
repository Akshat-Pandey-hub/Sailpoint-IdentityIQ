package com.keyforge.iiq.provisioningtransaction;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Maps a {@link ProvisioningTransaction} and one of its authoritative references into a
 * {@link ProvisioningEventLinkRow}. Pure and DB-free.
 *
 * <p>Deterministic PK per (transaction, link kind) — {@code kf_event_link|PROV_<KIND>|<txnid>} —
 * so a transaction yields at most one Request row and one Certification row, and reruns upsert the
 * same rows (never duplicate). The raw reference is preserved verbatim; the resolved target
 * type/id are carried only when the resolver returned RESOLVED.
 */
public final class ProvisioningEventLinkRowMapper {

    static final String SOURCE_TYPE = "ProvisioningTransaction";

    private ProvisioningEventLinkRowMapper() {
    }

    /** Request link, from the transaction's {@code accessRequestId}. */
    public static ProvisioningEventLinkRow mapRequestLink(ProvisioningTransaction t,
                                                          ProvisioningLinkResolver.Result r) {
        String txnId = toCanonicalUuid(t.id());
        return new ProvisioningEventLinkRow(
                deterministicId("PROV_REQUEST", txnId),
                txnId,
                SOURCE_TYPE,
                r.targetType(),
                r.targetId(),
                t.accessRequestId(),
                "Request",
                r.status().name(),
                r.rule());
    }

    /**
     * Certification link, from the transaction's {@code certificationName}. Always UNRESOLVED: the
     * transaction exposes a certification name (not a decision id) and certifications are not
     * extracted, so the raw name is preserved with no fabricated target id.
     */
    public static ProvisioningEventLinkRow mapCertificationLink(ProvisioningTransaction t) {
        String txnId = toCanonicalUuid(t.id());
        return new ProvisioningEventLinkRow(
                deterministicId("PROV_CERT", txnId),
                txnId,
                SOURCE_TYPE,
                null,
                null,
                t.certificationName(),
                "Certification",
                ProvisioningLinkResolver.Status.UNRESOLVED.name(),
                "certification not extracted (out of scope); name preserved, no decision id available");
    }

    private static String deterministicId(String kind, String canonicalTxnId) {
        return UUID.nameUUIDFromBytes(
                ("kf_event_link|" + kind + "|" + canonicalTxnId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ProvisioningEventLinkMappingException(
                    "ProvisioningTransaction id is missing; cannot form a kf_event_link key.");
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
            throw new ProvisioningEventLinkMappingException(
                    "ProvisioningTransaction id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }
}
