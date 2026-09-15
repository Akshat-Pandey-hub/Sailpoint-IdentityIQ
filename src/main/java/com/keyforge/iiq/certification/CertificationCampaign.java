package com.keyforge.iiq.certification;

/**
 * A SailPoint IdentityIQ certification campaign (a {@code CertificationGroup}) as returned by the
 * classic REST endpoint {@code POST /rest/certificationGroups} (verified live on this instance).
 * Captures only the fields that endpoint actually exposes — nothing is inferred.
 *
 * <p>Observed response object shape:
 * <pre>
 * { "id":"7f0001...", "name":"KF Test Targeted Cert", "ownerDisplayName":"Molly J",
 *   "status":"Active", "percentComplete":"0% (0 of 1)", "created":1789486968898, "tags":[] }
 * </pre>
 *
 * <p>The PDF's additional campaign attributes (type, phase, start/end dates, sign-off) are NOT
 * exposed by this endpoint and are therefore left absent here rather than fabricated. The individual
 * Certification / CertificationEntity / CertificationItem / CertificationAction hierarchy is not
 * reachable through a verified read-only interface on this instance (the admin drill-in route
 * returns HTTP 500; there is no plugin) and is intentionally out of scope for this model.
 *
 * @param id                the CertificationGroup id (32-char IIQ GUID)
 * @param name              campaign name
 * @param ownerDisplayName  display name of the campaign owner (no owner id is exposed)
 * @param status            campaign status, e.g. {@code Active}
 * @param percentComplete   completion display string, e.g. {@code "0% (0 of 1)"}
 * @param created           creation time as epoch milliseconds (UTC), or null if absent
 * @param tagsJson          the raw {@code tags} JSON array preserved verbatim (or null)
 */
public record CertificationCampaign(
        String id,
        String name,
        String ownerDisplayName,
        String status,
        String percentComplete,
        Long created,
        String tagsJson) {
}
