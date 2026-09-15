package com.keyforge.iiq.certification;

import java.time.LocalDateTime;

/**
 * A persistable {@code kf_certification_campaign} row. {@code campaignid} is the canonical UUID
 * derived from the IIQ CertificationGroup id; {@code sourceId} preserves the raw 32-char id.
 * {@code createdAt} is a UTC {@link LocalDateTime} converted from the source epoch-millis value.
 * Only fields the source exposes are present; PDF campaign attributes not exposed by the endpoint
 * (type, phase, start/end, sign-off) are deliberately absent and stored as NULL by the repository.
 */
public record CertificationCampaignRow(
        String campaignid,
        String sourceId,
        String name,
        String ownerDisplayName,
        String status,
        String percentComplete,
        LocalDateTime createdAt,
        String tagsJson) {
}
