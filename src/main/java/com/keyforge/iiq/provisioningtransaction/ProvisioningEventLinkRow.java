package com.keyforge.iiq.provisioningtransaction;

/**
 * A {@code kf_event_link} row for the <b>provisioning-transaction side</b> (source =
 * ProvisioningTransaction). Mirrors the audit side's row shape but the source object is carried in
 * the generic {@code source_object_id} column (the audit side uses {@code audit_event_id}); the two
 * are distinguished by {@code source_object_type}. Every row preserves the raw ProvisioningTransaction
 * id and the raw target reference so nothing is lost.
 *
 * @param id               deterministic UUID of (transaction | link kind) — rerun-stable
 * @param sourceObjectId   the ProvisioningTransaction id (canonical UUID), preserved
 * @param sourceObjectType always {@code "ProvisioningTransaction"}
 * @param targetObjectType resolved target type (AccessRequest / Certification), or null
 * @param targetObjectId   resolved target id (canonical UUID), or null when not resolved
 * @param targetRaw        the exact raw reference from the transaction (accessRequestId / certificationName)
 * @param targetTypeHint   the intended target kind (Request / Certification)
 * @param linkStatus       RESOLVED | UNRESOLVED | AMBIGUOUS
 * @param resolutionRule   short description of how resolution was decided
 */
public record ProvisioningEventLinkRow(
        String id,
        String sourceObjectId,
        String sourceObjectType,
        String targetObjectType,
        String targetObjectId,
        String targetRaw,
        String targetTypeHint,
        String linkStatus,
        String resolutionRule) {
}
